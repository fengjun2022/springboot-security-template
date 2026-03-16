#!/usr/bin/env python3
"""
Threat detection test script for the doc-admin project.

Scenarios covered:
- normal smoke requests
- SQL injection probe
- XSS probe
- path traversal probe
- scanner probe
- 401 feedback probing (unauthenticated)
- 403 feedback probing (requires a low-privilege token)
- non-destructive rate abuse (expect 429, no auto-blacklist)
- optional auto-blacklist test (destructive for current IP)
- admin event query / filter
- admin module batch toggle for api_endpoints.threat_monitor_enabled

Notes:
- This script uses the same client IP for all tests. If you enable auto-blacklist tests,
  your current IP may be blocked and all subsequent requests may be denied.
- For admin APIs and 403 testing, provide tokens or login credentials.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import sys
import time
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Any, Dict, Iterable, List, Optional, Tuple

try:
    import requests
except ImportError:
    print("Missing dependency: requests. Install it with: pip install requests", file=sys.stderr)
    raise


DEFAULT_SCENARIOS = [
    "smoke",
    "auth401_feedback",
    "auth403_feedback",
    "rate_limit",
    "xss",
    "scanner",
    "admin_events",
    "module_toggle",
    # destructive by current policy (will auto-blacklist on first hit)
    "path_traversal",
    "sqli",
]


def now_str() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def iso_now() -> str:
    return datetime.now().replace(microsecond=0).isoformat()


def trim_text(value: Optional[str], max_len: int = 280) -> str:
    if value is None:
        return ""
    text = value.replace("\n", " ").replace("\r", " ")
    if len(text) <= max_len:
        return text
    return text[: max_len - 3] + "..."


@dataclass
class CallResult:
    name: str
    method: str
    path: str
    status: Optional[int]
    ok: bool
    elapsed_ms: int
    error: Optional[str] = None
    body_preview: str = ""
    json_body: Optional[Dict[str, Any]] = None


@dataclass
class ScenarioResult:
    scenario: str
    success: bool
    summary: str
    details: List[CallResult] = field(default_factory=list)
    extra: Dict[str, Any] = field(default_factory=dict)


class HttpClient:
    def __init__(
        self,
        base_url: str,
        timeout: float = 5.0,
        simulated_ip: Optional[str] = None,
        simulated_ip_pool: Optional[List[str]] = None,
        ip_pool_mode: str = "scenario",
    ):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.simulated_ip = simulated_ip.strip() if simulated_ip else None
        self.simulated_ip_pool = [ip.strip() for ip in (simulated_ip_pool or []) if ip and ip.strip()]
        self.ip_pool_mode = ip_pool_mode
        self._ip_pool_index = 0
        self._scenario_ip: Optional[str] = None

    def _url(self, path: str) -> str:
        if path.startswith("http://") or path.startswith("https://"):
            return path
        if not path.startswith("/"):
            path = "/" + path
        return self.base_url + path

    def _next_pool_ip(self) -> Optional[str]:
        if not self.simulated_ip_pool:
            return None
        ip = self.simulated_ip_pool[self._ip_pool_index % len(self.simulated_ip_pool)]
        self._ip_pool_index += 1
        return ip

    def start_scenario(self, scenario_name: str) -> Optional[str]:
        if self.simulated_ip:
            self._scenario_ip = self.simulated_ip
            return self._scenario_ip
        if self.simulated_ip_pool and self.ip_pool_mode == "scenario":
            self._scenario_ip = self._next_pool_ip()
            return self._scenario_ip
        self._scenario_ip = None
        return self._scenario_ip

    def _resolve_simulated_ip_for_request(self) -> Optional[str]:
        if self.simulated_ip:
            return self.simulated_ip
        if self.simulated_ip_pool:
            if self.ip_pool_mode == "request":
                return self._next_pool_ip()
            return self._scenario_ip
        return None

    def _inject_forward_headers(self, headers: Optional[Dict[str, str]]) -> Optional[Dict[str, str]]:
        ip = self._resolve_simulated_ip_for_request()
        if not ip:
            return headers
        merged = dict(headers or {})
        # ThreatDetectionFilter 优先取 X-Forwarded-For，再取 X-Real-IP
        merged.setdefault("X-Forwarded-For", ip)
        merged.setdefault("X-Real-IP", ip)
        return merged

    def request(
        self,
        method: str,
        path: str,
        *,
        name: Optional[str] = None,
        headers: Optional[Dict[str, str]] = None,
        params: Optional[Dict[str, Any]] = None,
        json_data: Optional[Dict[str, Any]] = None,
        data: Optional[Any] = None,
        timeout: Optional[float] = None,
    ) -> CallResult:
        started = time.perf_counter()
        req_name = name or f"{method} {path}"
        try:
            headers = self._inject_forward_headers(headers)
            resp = self.session.request(
                method=method.upper(),
                url=self._url(path),
                headers=headers,
                params=params,
                json=json_data,
                data=data,
                timeout=timeout or self.timeout,
            )
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            body_text = resp.text or ""
            json_body = None
            try:
                json_body = resp.json()
            except Exception:
                pass
            return CallResult(
                name=req_name,
                method=method.upper(),
                path=path,
                status=resp.status_code,
                ok=resp.ok,
                elapsed_ms=elapsed_ms,
                body_preview=trim_text(body_text),
                json_body=json_body,
            )
        except requests.RequestException as exc:
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return CallResult(
                name=req_name,
                method=method.upper(),
                path=path,
                status=None,
                ok=False,
                elapsed_ms=elapsed_ms,
                error=str(exc),
            )

    def login(self, path: str, username: str, password: str) -> Tuple[Optional[str], CallResult]:
        result = self.request(
            "POST",
            path,
            name=f"login {path}",
            json_data={"username": username, "password": password},
        )
        token = None
        if result.json_body:
            data = result.json_body.get("data") if isinstance(result.json_body, dict) else None
            if isinstance(data, dict):
                token = data.get("token")
        return token, result


def bearer_headers(token: Optional[str]) -> Dict[str, str]:
    if not token:
        return {}
    return {"Authorization": "Bearer " + token}


def run_smoke(client: HttpClient) -> ScenarioResult:
    calls = [
        client.request("GET", "/test/a", name="public /test/a"),
        client.request("GET", "/api/test/example", name="public /api/test/example"),
        client.request("GET", "/api/test/param/1", name="path param /api/test/param/1"),
        client.request("GET", "/api/test/query", name="query /api/test/query", params={"name": "alice", "age": 18}),
    ]
    ok_count = sum(1 for c in calls if c.status == 200)
    return ScenarioResult("smoke", ok_count >= 3, f"200 responses: {ok_count}/{len(calls)}", calls)


def run_sqli(client: HttpClient) -> ScenarioResult:
    calls = [
        client.request(
            "GET",
            "/api/test/query",
            name="SQLi query probe",
            params={"name": "a' OR 1=1 --", "age": 1},
        ),
        client.request(
            "POST",
            "/api/test/example",
            name="SQLi body probe",
            headers={"Content-Type": "application/json"},
            data=json.dumps({"name": "admin' OR 1=1 --"}),
        ),
    ]
    blocked = [c for c in calls if c.status in (403, 429)]
    return ScenarioResult("sqli", len(blocked) >= 1, f"blocked={len(blocked)}/{len(calls)}", calls)


def run_xss(client: HttpClient) -> ScenarioResult:
    calls = [
        client.request(
            "GET",
            "/api/test/query",
            name="XSS query probe",
            params={"name": "<script>alert(1)</script>", "age": 1},
        ),
        client.request(
            "POST",
            "/api/test/example",
            name="XSS body probe",
            headers={"Content-Type": "application/json"},
            data='{"content":"<img src=x onerror=alert(1)>"}',
        ),
    ]
    blocked = [c for c in calls if c.status in (403, 429)]
    return ScenarioResult("xss", len(blocked) >= 1, f"blocked={len(blocked)}/{len(calls)}", calls)


def run_path_traversal(client: HttpClient) -> ScenarioResult:
    calls = [
        client.request("GET", "/api/test/param/%2e%2e%2fetc%2fpasswd", name="encoded traversal"),
        client.request("GET", "/..%2F..%2Fetc%2Fpasswd", name="raw traversal path"),
    ]
    blocked = [c for c in calls if c.status in (403, 429)]
    return ScenarioResult("path_traversal", len(blocked) >= 1, f"blocked={len(blocked)}/{len(calls)}", calls)


def run_scanner(client: HttpClient) -> ScenarioResult:
    calls = [
        client.request("GET", "/.env", name="scanner .env"),
        client.request("GET", "/wp-login.php", name="scanner wp-login"),
        client.request("GET", "/actuator/env", name="scanner actuator"),
    ]
    suspicious = [c for c in calls if c.status in (403, 404)]
    return ScenarioResult("scanner", len(suspicious) >= 1, f"suspicious_responses={len(suspicious)}/{len(calls)}", calls)


def run_auth401_feedback(client: HttpClient, repeat: int) -> ScenarioResult:
    calls: List[CallResult] = []
    for i in range(repeat):
        calls.append(client.request("GET", "/test/b", name=f"401 probe #{i+1}"))
    counts = Counter(c.status for c in calls)
    got_401_or_403 = (counts.get(401, 0) + counts.get(403, 0)) > 0
    summary = f"status_counts={dict(counts)} repeat={repeat}"
    return ScenarioResult("auth401_feedback", got_401_or_403, summary, calls, extra={"status_counts": dict(counts)})


def run_auth403_feedback(client: HttpClient, user_token: Optional[str], repeat: int) -> ScenarioResult:
    if not user_token:
        return ScenarioResult(
            "auth403_feedback",
            False,
            "skipped: missing user token/credentials (need USER token to hit /test/b and produce 403)",
        )
    calls: List[CallResult] = []
    headers = bearer_headers(user_token)
    for i in range(repeat):
        calls.append(client.request("GET", "/test/b", name=f"403 probe #{i+1}", headers=headers))
    counts = Counter(c.status for c in calls)
    got_403 = counts.get(403, 0) > 0
    summary = f"status_counts={dict(counts)} repeat={repeat}"
    return ScenarioResult("auth403_feedback", got_403, summary, calls, extra={"status_counts": dict(counts)})


def burst_requests(
    client: HttpClient,
    method: str,
    path: str,
    *,
    count: int,
    concurrency: int,
    headers: Optional[Dict[str, str]] = None,
) -> List[CallResult]:
    def one(i: int) -> CallResult:
        return client.request(method, path, name=f"burst #{i+1}", headers=headers, timeout=max(client.timeout, 10.0))

    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, concurrency)) as pool:
        futures = [pool.submit(one, i) for i in range(count)]
        return [f.result() for f in concurrent.futures.as_completed(futures)]


def run_rate_limit(client: HttpClient, count: int, concurrency: int) -> ScenarioResult:
    calls = burst_requests(client, "GET", "/test/a", count=count, concurrency=concurrency)
    counts = Counter(c.status for c in calls)
    got_429 = counts.get(429, 0) > 0
    summary = f"burst={count}, concurrency={concurrency}, status_counts={dict(counts)}"
    return ScenarioResult("rate_limit", got_429, summary, calls[:20], extra={"status_counts": dict(counts), "total_calls": count})


def run_auto_blacklist(client: HttpClient, count: int, concurrency: int) -> ScenarioResult:
    calls = burst_requests(client, "GET", "/test/a", count=count, concurrency=concurrency)
    counts = Counter(c.status for c in calls)
    verify = client.request("GET", "/test/a", name="verify blacklist after burst")
    summary = f"burst={count}, concurrency={concurrency}, status_counts={dict(counts)}, verify_status={verify.status}"
    success = verify.status == 403 or counts.get(403, 0) > 0
    details = [verify]
    return ScenarioResult(
        "auto_blacklist",
        success,
        summary,
        details,
        extra={"status_counts": dict(counts), "total_calls": count},
    )


def run_admin_events(
    client: HttpClient,
    admin_token: Optional[str],
    attack_type_filter: Optional[str] = None,
) -> ScenarioResult:
    if not admin_token:
        return ScenarioResult("admin_events", False, "skipped: missing admin token/credentials")
    headers = bearer_headers(admin_token)
    end = datetime.now().replace(microsecond=0)
    start = end - timedelta(minutes=10)
    params = {
        "page": 1,
        "size": 20,
        "startTime": start.isoformat(),
        "endTime": end.isoformat(),
    }
    if attack_type_filter:
        params["attackType"] = attack_type_filter

    calls = [
        client.request("GET", "/api/threat-detection/events/page", name="events page", headers=headers, params=params),
        client.request(
            "GET",
            "/api/threat-detection/events/page",
            name="events filter by IP",
            headers=headers,
            params={**params, "ip": "127.0.0.1"},
        ),
    ]
    ok = any(c.status == 200 for c in calls)
    return ScenarioResult("admin_events", ok, "queried /api/threat-detection/events/page", calls)


def run_module_toggle(client: HttpClient, admin_token: Optional[str], module_group: str) -> ScenarioResult:
    if not admin_token:
        return ScenarioResult("module_toggle", False, "skipped: missing admin token/credentials")
    headers = {**bearer_headers(admin_token), "Content-Type": "application/json"}
    calls = [
        client.request(
            "POST",
            "/api/threat-detection/endpoint-monitor/module-toggle",
            name="module toggle OFF",
            headers=headers,
            json_data={"moduleGroup": module_group, "threatMonitorEnabled": 0},
        ),
        client.request(
            "POST",
            "/api/threat-detection/endpoint-monitor/module-toggle",
            name="module toggle ON",
            headers=headers,
            json_data={"moduleGroup": module_group, "threatMonitorEnabled": 1},
        ),
    ]
    ok = all(c.status == 200 for c in calls)
    return ScenarioResult("module_toggle", ok, f"module_group={module_group}", calls)


def print_call(result: CallResult) -> None:
    status = result.status if result.status is not None else "ERR"
    print(f"  - {result.name}: status={status}, elapsed={result.elapsed_ms}ms")
    if result.error:
        print(f"      error: {result.error}")
    if result.body_preview:
        print(f"      body: {result.body_preview}")


def print_scenario(result: ScenarioResult) -> None:
    flag = "PASS" if result.success else "WARN"
    print(f"[{flag}] {result.scenario}: {result.summary}")
    for call in result.details[:10]:
        print_call(call)
    if len(result.details) > 10:
        print(f"  ... ({len(result.details) - 10} more calls omitted)")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Threat detection test script for doc-admin")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080", help="Server base URL")
    parser.add_argument("--timeout", type=float, default=5.0, help="HTTP timeout seconds")
    parser.add_argument("--sim-ip", default=None, help="Simulate a single client IP via X-Forwarded-For/X-Real-IP")
    parser.add_argument(
        "--sim-ip-pool",
        default=None,
        help="Comma-separated simulated IPs (e.g. 10.0.0.10,10.0.0.11,10.0.0.12)",
    )
    parser.add_argument(
        "--ip-pool-mode",
        choices=["scenario", "request"],
        default="scenario",
        help="How to rotate --sim-ip-pool: scenario=one IP per scenario, request=one IP per request",
    )
    parser.add_argument(
        "--scenarios",
        default="all",
        help="Comma-separated scenarios. Available: "
        + ",".join(DEFAULT_SCENARIOS + ["auto_blacklist"]),
    )

    parser.add_argument("--user-token", default=None, help="USER token (for 403 tests)")
    parser.add_argument("--admin-token", default=None, help="Admin token (for management API tests)")
    parser.add_argument("--user-username", default=None, help="Auto-login USER username")
    parser.add_argument("--user-password", default=None, help="Auto-login USER password")
    parser.add_argument("--admin-username", default=None, help="Auto-login ADMIN username")
    parser.add_argument("--admin-password", default=None, help="Auto-login ADMIN password")
    parser.add_argument("--user-login-path", default="/login", help="USER login path")
    parser.add_argument("--admin-login-path", default="/login-admin", help="ADMIN login path")

    parser.add_argument("--repeat-401", type=int, default=9, help="Repeat count for 401 feedback probe")
    parser.add_argument("--repeat-403", type=int, default=6, help="Repeat count for 403 feedback probe (keep below 12 to avoid auto-blacklist)")
    parser.add_argument("--rate-count", type=int, default=130, help="Burst count for non-destructive rate limit test")
    parser.add_argument("--rate-concurrency", type=int, default=30, help="Concurrency for rate limit tests")
    parser.add_argument("--auto-blacklist-count", type=int, default=380, help="Burst count for auto-blacklist test (destructive)")
    parser.add_argument("--auto-blacklist-concurrency", type=int, default=60, help="Concurrency for auto-blacklist test")
    parser.add_argument("--module-group", default="测试接口", help="Module group for batch monitor toggle")
    parser.add_argument("--events-attack-type", default=None, help="Optional attackType filter for admin events query")
    parser.add_argument("--output-json", default=None, help="Write scenario summary JSON to file")
    return parser.parse_args()


def resolve_scenarios(value: str) -> List[str]:
    if value.strip().lower() == "all":
        return list(DEFAULT_SCENARIOS)
    selected = [s.strip() for s in value.split(",") if s.strip()]
    valid = set(DEFAULT_SCENARIOS + ["auto_blacklist"])
    invalid = [s for s in selected if s not in valid]
    if invalid:
        raise ValueError(f"Unknown scenarios: {invalid}")
    return selected


def maybe_login_tokens(client: HttpClient, args: argparse.Namespace) -> Tuple[Optional[str], Optional[str], List[CallResult]]:
    login_calls: List[CallResult] = []
    user_token = args.user_token
    admin_token = args.admin_token

    if not user_token and args.user_username and args.user_password:
        token, call = client.login(args.user_login_path, args.user_username, args.user_password)
        login_calls.append(call)
        user_token = token

    if not admin_token and args.admin_username and args.admin_password:
        token, call = client.login(args.admin_login_path, args.admin_username, args.admin_password)
        login_calls.append(call)
        admin_token = token

    return user_token, admin_token, login_calls


def main() -> int:
    args = parse_args()
    try:
        scenarios = resolve_scenarios(args.scenarios)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    print(f"[{now_str()}] Base URL: {args.base_url}")
    print(f"[{now_str()}] Scenarios: {', '.join(scenarios)}")

    if "auto_blacklist" in scenarios:
        print("WARNING: auto_blacklist scenario may block your current IP from all endpoints.", file=sys.stderr)

    ip_pool = [s.strip() for s in args.sim_ip_pool.split(",")] if args.sim_ip_pool else None
    client = HttpClient(
        args.base_url,
        timeout=args.timeout,
        simulated_ip=args.sim_ip,
        simulated_ip_pool=ip_pool,
        ip_pool_mode=args.ip_pool_mode,
    )
    user_token, admin_token, login_calls = maybe_login_tokens(client, args)
    for call in login_calls:
        print_call(call)

    all_results: List[ScenarioResult] = []

    handlers = {
        "smoke": lambda: run_smoke(client),
        "sqli": lambda: run_sqli(client),
        "xss": lambda: run_xss(client),
        "path_traversal": lambda: run_path_traversal(client),
        "scanner": lambda: run_scanner(client),
        "auth401_feedback": lambda: run_auth401_feedback(client, args.repeat_401),
        "auth403_feedback": lambda: run_auth403_feedback(client, user_token, args.repeat_403),
        "rate_limit": lambda: run_rate_limit(client, args.rate_count, args.rate_concurrency),
        "auto_blacklist": lambda: run_auto_blacklist(
            client, args.auto_blacklist_count, args.auto_blacklist_concurrency
        ),
        "admin_events": lambda: run_admin_events(client, admin_token, args.events_attack_type),
        "module_toggle": lambda: run_module_toggle(client, admin_token, args.module_group),
    }

    # Run in a safe order. Destructive test must be last.
    ordered = [s for s in scenarios if s != "auto_blacklist"]
    if "auto_blacklist" in scenarios:
        ordered.append("auto_blacklist")

    for scenario in ordered:
        active_ip = client.start_scenario(scenario)
        print(f"\n[{now_str()}] Running scenario: {scenario}")
        if active_ip:
            print(f"[{now_str()}] Simulated client IP for scenario '{scenario}': {active_ip}")
        result = handlers[scenario]()
        all_results.append(result)
        print_scenario(result)

    passed = sum(1 for r in all_results if r.success)
    print(f"\n[{now_str()}] Done. pass={passed}/{len(all_results)}")

    if args.output_json:
        payload = {
            "baseUrl": args.base_url,
            "generatedAt": iso_now(),
            "results": [
                {
                    "scenario": r.scenario,
                    "success": r.success,
                    "summary": r.summary,
                    "extra": r.extra,
                    "details": [
                        {
                            "name": d.name,
                            "method": d.method,
                            "path": d.path,
                            "status": d.status,
                            "ok": d.ok,
                            "elapsedMs": d.elapsed_ms,
                            "error": d.error,
                            "bodyPreview": d.body_preview,
                        }
                        for d in r.details
                    ],
                }
                for r in all_results
            ],
        }
        with open(args.output_json, "w", encoding="utf-8") as f:
            json.dump(payload, f, ensure_ascii=False, indent=2)
        print(f"[{now_str()}] Wrote JSON report: {args.output_json}")

    # Non-zero exit only if all scenarios failed (so partial data still useful).
    return 0 if passed > 0 else 1


if __name__ == "__main__":
    sys.exit(main())

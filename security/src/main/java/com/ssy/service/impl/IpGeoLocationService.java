package com.ssy.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IpGeoLocationService {

    private final Map<String, GeoInfo> cache = new ConcurrentHashMap<>();

    public GeoInfo resolve(String ip) {
        if (!StringUtils.hasText(ip)) {
            return GeoInfo.unknown();
        }
        String normalizedIp = ip.trim();
        GeoInfo cached = cache.get(normalizedIp);
        if (cached != null) {
            return cached;
        }
        GeoInfo resolved = fetch(normalizedIp);
        cache.put(normalizedIp, resolved);
        return resolved;
    }

    private GeoInfo fetch(String ip) {
        if (isLocalOrPrivateIp(ip)) {
            return new GeoInfo("内网", "内网", "内网", "LOCAL", "内网地址");
        }
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://ipwho.is/" + ip);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(2000);
            connection.setRequestProperty("Accept", "application/json");

            try (InputStream inputStream = connection.getInputStream()) {
                String body = readBody(inputStream);
                JSONObject payload = JSON.parseObject(body);
                if (payload == null || !payload.getBooleanValue("success")) {
                    return GeoInfo.unknown();
                }
                String country = payload.getString("country");
                String region = payload.getString("region");
                String city = payload.getString("city");
                JSONObject connectionPayload = payload.getJSONObject("connection");
                String isp = connectionPayload == null ? null : connectionPayload.getString("isp");
                String locationLabel = buildLocationLabel(country, region, city);
                return new GeoInfo(country, region, city, isp, locationLabel);
            }
        } catch (Exception ignored) {
            return GeoInfo.unknown();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readBody(InputStream inputStream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, len);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private boolean isLocalOrPrivateIp(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String buildLocationLabel(String country, String region, String city) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(country)) {
            builder.append(country);
        }
        if (StringUtils.hasText(region)) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(region);
        }
        if (StringUtils.hasText(city)) {
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(city);
        }
        return builder.length() == 0 ? "未知" : builder.toString();
    }

    public static class GeoInfo {
        private final String country;
        private final String regionName;
        private final String city;
        private final String isp;
        private final String locationLabel;

        public GeoInfo(String country, String regionName, String city, String isp, String locationLabel) {
            this.country = country;
            this.regionName = regionName;
            this.city = city;
            this.isp = isp;
            this.locationLabel = locationLabel;
        }

        public static GeoInfo unknown() {
            return new GeoInfo("未知", "未知", "未知", "未知", "未知");
        }

        public String getCountry() {
            return country;
        }

        public String getRegionName() {
            return regionName;
        }

        public String getCity() {
            return city;
        }

        public String getIsp() {
            return isp;
        }

        public String getLocationLabel() {
            return locationLabel;
        }
    }
}

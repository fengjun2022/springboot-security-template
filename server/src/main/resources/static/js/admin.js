// 管理系统公共JavaScript函数

/**
 * 通用工具类
 */
const AdminUtils = {
    /**
     * 显示消息提示
     */
    showMessage: function (message, type = 'info', duration = 3000) {
        const alertClass = type === 'success' ? 'alert-success' :
            type === 'error' ? 'alert-danger' :
                type === 'warning' ? 'alert-warning' : 'alert-info';

        const alertHtml = `
            <div class="alert ${alertClass} alert-dismissible fade show position-fixed" 
                 style="top: 20px; right: 20px; z-index: 9999; min-width: 300px;">
                ${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;

        $('body').append(alertHtml);

        // 自动消失
        setTimeout(function () {
            $('.alert').fadeOut();
        }, duration);
    },

    /**
     * 确认对话框
     */
    confirm: function (message, callback) {
        if (confirm(message)) {
            callback();
        }
    },

    /**
     * 格式化日期
     */
    formatDate: function (date, format = 'YYYY-MM-DD HH:mm:ss') {
        if (!date) return '-';

        const d = new Date(date);
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        const seconds = String(d.getSeconds()).padStart(2, '0');

        return format.replace('YYYY', year)
            .replace('MM', month)
            .replace('DD', day)
            .replace('HH', hours)
            .replace('mm', minutes)
            .replace('ss', seconds);
    },

    /**
     * 格式化文件大小
     */
    formatFileSize: function (bytes) {
        if (bytes === 0) return '0 Bytes';

        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));

        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    },

    /**
     * 复制到剪贴板
     */
    copyToClipboard: function (text) {
        if (navigator.clipboard) {
            navigator.clipboard.writeText(text).then(function () {
                AdminUtils.showMessage('已复制到剪贴板', 'success');
            }).catch(function () {
                AdminUtils.showMessage('复制失败', 'error');
            });
        } else {
            // 兼容旧浏览器
            const textArea = document.createElement('textarea');
            textArea.value = text;
            document.body.appendChild(textArea);
            textArea.select();
            try {
                document.execCommand('copy');
                AdminUtils.showMessage('已复制到剪贴板', 'success');
            } catch (err) {
                AdminUtils.showMessage('复制失败', 'error');
            }
            document.body.removeChild(textArea);
        }
    },

    /**
     * 防抖函数
     */
    debounce: function (func, wait, immediate) {
        let timeout;
        return function executedFunction() {
            const context = this;
            const args = arguments;
            const later = function () {
                timeout = null;
                if (!immediate) func.apply(context, args);
            };
            const callNow = immediate && !timeout;
            clearTimeout(timeout);
            timeout = setTimeout(later, wait);
            if (callNow) func.apply(context, args);
        };
    },

    /**
     * 节流函数
     */
    throttle: function (func, limit) {
        let inThrottle;
        return function () {
            const args = arguments;
            const context = this;
            if (!inThrottle) {
                func.apply(context, args);
                inThrottle = true;
                setTimeout(() => inThrottle = false, limit);
            }
        };
    }
};

/**
 * Token管理工具
 */
const AdminToken = {
    COOKIE_NAME: 'admin_token',
    COOKIE_EXPIRES_DAYS: 7,

    /**
     * 保存token到localStorage和Cookie
     */
    setToken: function (token) {
        if (token) {
            // 保存到localStorage
            localStorage.setItem('admin_token', token);
            console.log('✅ Token已保存到localStorage');

            // 保存到Cookie
            this.setCookie(token);
            console.log('🍪 Token已保存到Cookie');

            console.log('Token已保存:', token.substring(0, 20) + '...');
        }
    },

    /**
     * 从localStorage获取token（增强版）
     */
    getToken: function () {
        try {
            const token = localStorage.getItem('admin_token');
            if (token && token.trim().length > 0) {
                // 简单的token格式验证
                if (token.split('.').length === 3) {
                    return token;
                } else {
                    console.warn('⚠️ AdminToken: Invalid token format, clearing...');
                    this.clearToken();
                    return null;
                }
            }
            return null;
        } catch (error) {
            console.error('❌ AdminToken: Error getting token:', error);
            return null;
        }
    },

    /**
     * 设置Cookie
     */
    setCookie: function (token) {
        try {
            const expires = new Date();
            expires.setTime(expires.getTime() + (this.COOKIE_EXPIRES_DAYS * 24 * 60 * 60 * 1000));
            document.cookie = `${this.COOKIE_NAME}=${token}; expires=${expires.toUTCString()}; path=/; SameSite=Lax`;
            console.log('🍪 Cookie已设置，有效期7天');
        } catch (error) {
            console.error('❌ 设置Cookie失败:', error);
        }
    },

    /**
     * 获取Cookie
     */
    getCookie: function () {
        try {
            const name = this.COOKIE_NAME + '=';
            const decodedCookie = decodeURIComponent(document.cookie);
            const cookieArray = decodedCookie.split(';');

            for (let i = 0; i < cookieArray.length; i++) {
                let cookie = cookieArray[i];
                while (cookie.charAt(0) === ' ') {
                    cookie = cookie.substring(1);
                }
                if (cookie.indexOf(name) === 0) {
                    return cookie.substring(name.length, cookie.length);
                }
            }
            return null;
        } catch (error) {
            console.error('❌ 获取Cookie失败:', error);
            return null;
        }
    },

    /**
     * 清除token
     */
    clearToken: function () {
        // 清除localStorage
        localStorage.removeItem('admin_token');
        console.log('🗑️ localStorage中的token已清除');

        // 清除Cookie
        this.clearCookie();
        console.log('🗑️ Cookie中的token已清除');

        console.log('Token已清除');
    },

    /**
     * 清除Cookie
     */
    clearCookie: function () {
        try {
            document.cookie = `${this.COOKIE_NAME}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`;
            console.log('🍪 Cookie已清除');
        } catch (error) {
            console.error('❌ 清除Cookie失败:', error);
        }
    },

    /**
     * 检查是否已登录
     */
    isLoggedIn: function () {
        const token = this.getToken();
        return token && token.trim().length > 0;
    },

    /**
     * 获取完整的Authorization头
     */
    getAuthorizationHeader: function () {
        const token = this.getToken();
        return token ? 'Bearer ' + token : null;
    }
};

/**
 * AJAX请求封装
 */
const AdminAjax = {
    /**
     * 通用AJAX请求
     */
    request: function (options) {
        const defaults = {
            method: 'GET',
            contentType: 'application/json',
            dataType: 'json',
            timeout: 30000,
            beforeSend: function (xhr) {
                // 显示加载状态
                AdminAjax.showLoading();

                // 双层保险：自动添加Authorization头
                AdminAjax.ensureAuthorizationHeader(xhr);
            },
            complete: function () {
                // 隐藏加载状态
                AdminAjax.hideLoading();
            },
            error: function (xhr, status, error) {
                // 处理401未授权错误
                if (xhr.status === 401) {
                    AdminUtils.showMessage('登录已过期，请重新登录', 'warning');
                    AdminToken.clearToken();
                    // 可以跳转到登录页面
                    setTimeout(function () {
                        window.location.href = '/admin/login';
                    }, 2000);
                } else {
                    AdminUtils.showMessage('请求失败: ' + error, 'error');
                }
            }
        };

        const settings = $.extend({}, defaults, options);

        // 如果用户提供了自己的beforeSend，需要合并两个函数
        if (options && options.beforeSend) {
            const originalBeforeSend = options.beforeSend;
            settings.beforeSend = function (xhr) {
                // 先执行默认的beforeSend（包含token添加）
                defaults.beforeSend.call(this, xhr);
                // 再执行用户自定义的beforeSend
                originalBeforeSend.call(this, xhr);
            };
        }

        return $.ajax(settings);
    },

    /**
     * 确保Authorization头被正确添加（双层保险）
     */
    ensureAuthorizationHeader: function (xhr) {
        try {
            // 第一层：检查是否已经有Authorization头
            const existingAuth = xhr.getRequestHeader('Authorization');
            if (!existingAuth) {
                // 第二层：从localStorage获取token并添加
                const token = AdminToken.getToken();
                if (token) {
                    const authHeader = 'Bearer ' + token;
                    xhr.setRequestHeader('Authorization', authHeader);
                    console.log('🔑 AdminAjax: Token added to request -', token.substring(0, 20) + '...');
                } else {
                    console.warn('⚠️ AdminAjax: No token found in localStorage');
                }
            } else {
                console.log('🔑 AdminAjax: Authorization header already exists');
            }
        } catch (error) {
            console.error('❌ AdminAjax: Error setting Authorization header:', error);
        }
    },

    /**
     * 设置全局AJAX拦截器（第三层保险）
     */
    setupGlobalInterceptors: function () {
        // jQuery AJAX全局事件处理器
        $(document).ajaxSend(function (event, xhr, settings) {
            // 跳过登录请求本身
            if (settings.url && settings.url.includes('/login')) {
                return;
            }

            // 检查是否已经设置了Authorization头
            const existingAuth = xhr.getRequestHeader('Authorization');
            if (!existingAuth) {
                const token = AdminToken.getToken();
                if (token) {
                    const authHeader = 'Bearer ' + token;
                    xhr.setRequestHeader('Authorization', authHeader);
                    console.log('🔑 Global: Token added by interceptor -', token.substring(0, 20) + '...');
                }
            }
        });

        // 全局AJAX错误处理器
        $(document).ajaxError(function (event, xhr, settings, thrownError) {
            if (xhr.status === 401) {
                console.warn('🔒 Global: 401 Unauthorized detected, clearing token');
                AdminToken.clearToken();
                AdminUtils.showMessage('登录已过期，请重新登录', 'warning');

                // 延迟跳转，避免干扰其他错误处理
                setTimeout(function () {
                    if (window.location.pathname !== '/admin/login') {
                        window.location.href = '/admin/login';
                    }
                }, 2000);
            }
        });

        console.log('🔧 AdminAjax: Global interceptors setup complete');
    },

    /**
     * GET请求
     */
    get: function (url, data, success, error) {
        return this.request({
            url: url,
            method: 'GET',
            data: data,
            success: success,
            error: error
        });
    },

    /**
     * POST请求
     */
    post: function (url, data, success, error) {
        return this.request({
            url: url,
            method: 'POST',
            data: typeof data === 'string' ? data : JSON.stringify(data),
            success: success,
            error: error
        });
    },

    /**
     * PUT请求
     */
    put: function (url, data, success, error) {
        return this.request({
            url: url,
            method: 'PUT',
            data: typeof data === 'string' ? data : JSON.stringify(data),
            success: success,
            error: error
        });
    },

    /**
     * DELETE请求
     */
    delete: function (url, success, error) {
        return this.request({
            url: url,
            method: 'DELETE',
            success: success,
            error: error
        });
    },

    /**
     * 显示加载状态
     */
    showLoading: function () {
        if ($('#globalLoading').length === 0) {
            const loadingHtml = `
                <div id="globalLoading" class="position-fixed d-flex align-items-center justify-content-center" 
                     style="top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.3); z-index: 9999;">
                    <div class="spinner-border text-primary" role="status">
                        <span class="visually-hidden">加载中...</span>
                    </div>
                </div>
            `;
            $('body').append(loadingHtml);
        }
    },

    /**
     * 隐藏加载状态
     */
    hideLoading: function () {
        $('#globalLoading').remove();
    }
};

/**
 * 表格工具类
 */
const AdminTable = {
    /**
     * 初始化表格
     */
    init: function (tableId, options = {}) {
        const defaults = {
            pageSize: 20,
            showPagination: true,
            showSearch: true,
            selectable: false
        };

        const settings = $.extend({}, defaults, options);

        // 初始化分页
        if (settings.showPagination) {
            this.initPagination(tableId, settings);
        }

        // 初始化搜索
        if (settings.showSearch) {
            this.initSearch(tableId, settings);
        }

        // 初始化选择功能
        if (settings.selectable) {
            this.initSelection(tableId, settings);
        }
    },

    /**
     * 初始化分页
     */
    initPagination: function (tableId, settings) {
        // 分页逻辑
    },

    /**
     * 初始化搜索
     */
    initSearch: function (tableId, settings) {
        // 搜索逻辑
    },

    /**
     * 初始化选择功能
     */
    initSelection: function (tableId, settings) {
        // 全选功能
        $(tableId + ' #selectAll').change(function () {
            $(tableId + ' .row-checkbox').prop('checked', this.checked);
        });

        // 单个选择
        $(tableId + ' .row-checkbox').change(function () {
            const total = $(tableId + ' .row-checkbox').length;
            const checked = $(tableId + ' .row-checkbox:checked').length;
            $(tableId + ' #selectAll').prop('checked', total === checked);
        });
    },

    /**
     * 获取选中的行
     */
    getSelectedRows: function (tableId) {
        return $(tableId + ' .row-checkbox:checked').map(function () {
            return this.value;
        }).get();
    },

    /**
     * 刷新表格
     */
    refresh: function (tableId) {
        // 重新加载表格数据
        location.reload();
    }
};

/**
 * 表单工具类
 */
const AdminForm = {
    /**
     * 表单验证
     */
    validate: function (formId, rules) {
        let isValid = true;
        const form = $(formId);

        // 清除之前的错误提示
        form.find('.is-invalid').removeClass('is-invalid');
        form.find('.invalid-feedback').remove();

        // 验证每个字段
        for (const field in rules) {
            const input = form.find(`[name="${field}"]`);
            const rule = rules[field];
            const value = input.val();

            // 必填验证
            if (rule.required && !value) {
                this.showFieldError(input, rule.message || '此字段为必填项');
                isValid = false;
                continue;
            }

            // 长度验证
            if (rule.minLength && value.length < rule.minLength) {
                this.showFieldError(input, `长度不能少于${rule.minLength}个字符`);
                isValid = false;
                continue;
            }

            if (rule.maxLength && value.length > rule.maxLength) {
                this.showFieldError(input, `长度不能超过${rule.maxLength}个字符`);
                isValid = false;
                continue;
            }

            // 正则验证
            if (rule.pattern && !rule.pattern.test(value)) {
                this.showFieldError(input, rule.message || '格式不正确');
                isValid = false;
                continue;
            }

            // 自定义验证
            if (rule.validator && !rule.validator(value)) {
                this.showFieldError(input, rule.message || '验证失败');
                isValid = false;
                continue;
            }
        }

        return isValid;
    },

    /**
     * 显示字段错误
     */
    showFieldError: function (input, message) {
        input.addClass('is-invalid');
        input.after(`<div class="invalid-feedback">${message}</div>`);
    },

    /**
     * 重置表单
     */
    reset: function (formId) {
        const form = $(formId);
        form[0].reset();
        form.find('.is-invalid').removeClass('is-invalid');
        form.find('.invalid-feedback').remove();
    },

    /**
     * 序列化表单为JSON
     */
    serialize: function (formId) {
        const form = $(formId);
        const data = {};

        form.serializeArray().forEach(function (item) {
            if (data[item.name]) {
                if (Array.isArray(data[item.name])) {
                    data[item.name].push(item.value);
                } else {
                    data[item.name] = [data[item.name], item.value];
                }
            } else {
                data[item.name] = item.value;
            }
        });

        // 处理多选框
        form.find('select[multiple]').each(function () {
            const name = $(this).attr('name');
            data[name] = $(this).val() || [];
        });

        return data;
    }
};

/**
 * 页面初始化
 */
$(document).ready(function () {
    // 初始化工具提示
    var tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    var tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });

    // 初始化弹出框
    var popoverTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="popover"]'));
    var popoverList = popoverTriggerList.map(function (popoverTriggerEl) {
        return new bootstrap.Popover(popoverTriggerEl);
    });

    // 侧边栏激活状态
    const currentPath = window.location.pathname;
    $('.sidebar .nav-link').each(function () {
        if ($(this).attr('href') === currentPath) {
            $(this).addClass('active');
        }
    });

    // 表格行点击高亮
    $(document).on('click', '.table tbody tr', function () {
        $(this).toggleClass('table-active');
    });

    // 搜索框增强
    $(document).on('keyup', '.search-input', AdminUtils.debounce(function () {
        const keyword = $(this).val();
        // 触发搜索
        console.log('搜索:', keyword);
    }, 300));
});

/**
 * 通用刷新页面函数
 */
function refreshPage() {
    location.reload();
}

/**
 * 通用返回函数
 */
function goBack() {
    history.back();
}

/**
 * 导出表格数据
 */
function exportTable(format = 'csv') {
    AdminUtils.showMessage('导出功能开发中...', 'info');
}

/**
 * 全屏切换
 */
function toggleFullScreen() {
    if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen();
    } else {
        if (document.exitFullscreen) {
            document.exitFullscreen();
        }
    }
}

/**
 * 登录功能管理
 */
const AdminLogin = {
    /**
     * 初始化登录页面
     */
    init: function () {
        const loginForm = $('#loginForm');
        if (loginForm.length > 0) {
            this.bindLoginForm();
        }
    },

    /**
     * 绑定登录表单事件
     */
    bindLoginForm: function () {
        const self = this;

        // 登录表单提交
        $('#loginForm').on('submit', function (e) {
            e.preventDefault();
            self.login();
        });

        // 回车键提交
        $('#username, #password').on('keypress', function (e) {
            if (e.which === 13) {
                self.login();
            }
        });

        // 记住我功能
        $('#rememberMe').on('change', function () {
            localStorage.setItem('remember_me', this.checked);
        });
    },

    /**
     * 执行登录
     */
    login: function () {
        const username = $('#username').val().trim();
        const password = $('#password').val().trim();

        if (!username) {
            AdminUtils.showMessage('请输入用户名', 'warning');
            $('#username').focus();
            return;
        }

        if (!password) {
            AdminUtils.showMessage('请输入密码', 'warning');
            $('#password').focus();
            return;
        }

        // 显示加载状态
        const loginBtn = $('#loginBtn');
        const originalText = loginBtn.text();
        loginBtn.prop('disabled', true).text('登录中...');

        // 发送登录请求
        $.ajax({
            url: '/login',
            method: 'POST',
            contentType: 'application/json',
            data: JSON.stringify({
                username: username,
                password: password
            }),
            success: function (response) {
                console.log('登录响应:', response);

                if (response.code === 200 && response.data) {
                    // 保存token
                    if (response.data.token) {
                        AdminToken.setToken(response.data.token);
                        AdminUtils.showMessage('登录成功，正在跳转...', 'success');
                        console.log('Token已保存，将跳转到管理页面');

                        // 跳转到管理页面
                        setTimeout(function () {
                            window.location.href = '/admin/dashboard';
                        }, 1000);
                    } else {
                        AdminUtils.showMessage('登录成功但未获取到token', 'warning');
                    }
                } else {
                    AdminUtils.showMessage(response.msg || '登录失败', 'error');
                }
            },
            error: function (xhr, status, error) {
                console.error('登录请求失败:', xhr, status, error);

                let errorMsg = '登录失败';
                if (xhr.responseJSON && xhr.responseJSON.msg) {
                    errorMsg = xhr.responseJSON.msg;
                } else if (xhr.status === 401) {
                    errorMsg = '用户名或密码错误';
                } else if (xhr.status === 0) {
                    errorMsg = '无法连接到服务器';
                }

                AdminUtils.showMessage(errorMsg, 'error');
            },
            complete: function () {
                // 恢复按钮状态
                loginBtn.prop('disabled', false).text(originalText);
            }
        });
    },

    /**
     * 登出
     */
    logout: function () {
        AdminToken.clearToken();
        AdminUtils.showMessage('已登出', 'info');

        // 跳转到登录页面
        setTimeout(function () {
            window.location.href = '/admin/login';
        }, 1000);
    },

    /**
     * 检查登录状态
     */
    checkLoginStatus: function () {
        const token = AdminToken.getToken();
        if (!token) {
            console.log('未找到token，跳转到登录页面');
            window.location.href = '/admin/login';
            return false;
        }

        // 可以在这里添加token验证逻辑
        return true;
    }
};

// 页面加载完成后初始化登录功能
$(document).ready(function () {
    const currentPath = window.location.pathname;

    console.log('🔧 Admin.js 初始化，当前页面:', currentPath);

    // 只在登录页面初始化登录功能
    if (currentPath === '/admin/login') {
        console.log('🔐 初始化登录页面功能');
        AdminLogin.init();
    }

    // 在所有管理页面设置全局AJAX拦截器
    if (currentPath.startsWith('/admin')) {
        console.log('🌐 设置全局AJAX拦截器');
        AdminAjax.setupGlobalInterceptors();
    }

    // 检查登录状态（在非登录页面）
    if (currentPath !== '/admin/login' && currentPath.startsWith('/admin')) {
        console.log('🔍 检查登录状态...');

        // 延迟检查，确保所有初始化完成
        setTimeout(function () {
            if (!AdminToken.isLoggedIn()) {
                console.warn('❌ 未检测到登录状态，跳转到登录页面');
                AdminUtils.showMessage('请先登录', 'warning');
                setTimeout(function () {
                    window.location.href = '/admin/login';
                }, 1000);
            } else {
                console.log('✅ 登录状态正常');
                // 显示当前用户信息
                const token = AdminToken.getToken();
                if (token) {
                    console.log('🔑 Token存在:', token.substring(0, 20) + '...');
                }
            }
        }, 100);
    }

    console.log('✅ Admin.js 初始化完成');
});

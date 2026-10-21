(function () {
  'use strict';

  var KEYS = {
    token: 'atw.admin.token',
    user: 'atw.admin.user',
    username: 'atw.admin.username'
  };

  function getToken() {
    return localStorage.getItem(KEYS.token) || '';
  }

  function getUser() {
    var raw = localStorage.getItem(KEYS.user);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function saveUser(user) {
    if (user) {
      localStorage.setItem(KEYS.user, JSON.stringify(user));
    }
  }

  function setAuth(token, user) {
    localStorage.setItem(KEYS.token, token);
    saveUser(user);
  }

  function clearAuth() {
    localStorage.removeItem(KEYS.token);
    localStorage.removeItem(KEYS.user);
  }

  function loginUrl(reason) {
    return 'login.html' + (reason ? '?reason=' + encodeURIComponent(reason) : '');
  }

  function redirectToLogin(reason) {
    window.location.replace(loginUrl(reason));
  }

  function errorMessage(payload, status) {
    if (payload && payload.data && typeof payload.data === 'object' && !Array.isArray(payload.data)) {
      var values = Object.keys(payload.data).map(function (key) {
        return payload.data[key];
      }).filter(function (value) {
        return typeof value === 'string' && value.length > 0;
      });
      if (values.length > 0) {
        return values[0];
      }
    }
    if (payload && payload.message) {
      return payload.message;
    }
    if (status === 403) {
      return '没有权限执行该操作';
    }
    return '请求失败（HTTP ' + status + '）';
  }

  async function api(path, options) {
    var settings = options || {};
    var method = settings.method || 'GET';
    var auth = settings.auth !== false;
    var redirectOn401 = settings.redirectOn401 !== false;
    var timeout = settings.timeout || 20000;
    var headers = Object.assign({ Accept: 'application/json' }, settings.headers || {});
    var body = settings.body;

    if (auth) {
      var token = getToken();
      if (token) {
        headers.Authorization = 'Bearer ' + token;
      }
    }

    if (body !== undefined && body !== null && !(body instanceof FormData)) {
      headers['Content-Type'] = 'application/json';
    }

    var controller = new AbortController();
    var timer = setTimeout(function () {
      controller.abort();
    }, timeout);
    var response;

    try {
      response = await fetch(path, {
        method: method,
        headers: headers,
        body: body === undefined || body === null
          ? undefined
          : (body instanceof FormData ? body : JSON.stringify(body)),
        signal: controller.signal,
        credentials: 'same-origin'
      });
    } catch (e) {
      clearTimeout(timer);
      if (e && e.name === 'AbortError') {
        throw new Error('请求超时，请稍后重试');
      }
      throw new Error('无法连接服务端，请检查网络或服务状态');
    }
    clearTimeout(timer);

    var text = await response.text();
    var payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch (e) {
        payload = null;
      }
    }

    if (!response.ok) {
      if (response.status === 401 && auth && redirectOn401) {
        clearAuth();
        redirectToLogin('expired');
      }
      var error = new Error(errorMessage(payload, response.status));
      error.status = response.status;
      error.payload = payload;
      throw error;
    }

    return payload;
  }

  function setButtonLoading(button, loading, loadingText) {
    if (!button) {
      return;
    }
    if (loading) {
      button.dataset.originalLabel = button.textContent;
      button.disabled = true;
      button.classList.add('is-loading');
      button.textContent = loadingText || '处理中...';
    } else {
      button.disabled = false;
      button.classList.remove('is-loading');
      if (button.dataset.originalLabel) {
        button.textContent = button.dataset.originalLabel;
        delete button.dataset.originalLabel;
      }
    }
  }

  async function copyText(text) {
    if (!text) {
      return false;
    }
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(text);
        return true;
      }
    } catch (e) {
    }

    var area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', 'readonly');
    area.style.position = 'fixed';
    area.style.left = '-9999px';
    area.style.top = '0';
    document.body.appendChild(area);
    area.select();
    var copied = false;
    try {
      copied = document.execCommand('copy');
    } catch (e) {
      copied = false;
    }
    document.body.removeChild(area);
    return copied;
  }

  function toast(message, type) {
    var root = document.getElementById('toastRoot');
    if (!root) {
      return;
    }
    var node = document.createElement('div');
    node.className = 'toast toast-' + (type || 'info');
    node.setAttribute('role', 'status');

    var mark = document.createElement('span');
    mark.className = 'toast-mark';
    mark.setAttribute('aria-hidden', 'true');

    var text = document.createElement('span');
    text.className = 'toast-text';
    text.textContent = message;

    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'toast-close';
    close.setAttribute('aria-label', '关闭提示');
    close.textContent = '\u00d7';

    node.appendChild(mark);
    node.appendChild(text);
    node.appendChild(close);
    root.appendChild(node);

    requestAnimationFrame(function () {
      node.classList.add('is-visible');
    });

    var timer = setTimeout(dismiss, 4200);

    function dismiss() {
      clearTimeout(timer);
      node.classList.remove('is-visible');
      setTimeout(function () {
        if (node.parentNode) {
          node.parentNode.removeChild(node);
        }
      }, 180);
    }

    close.addEventListener('click', dismiss);
  }

  function pad(value) {
    return value < 10 ? '0' + value : String(value);
  }

  function formatDateTime(value) {
    if (!value) {
      return '\u2014';
    }
    var text = String(value);
    if (text.length >= 16) {
      return text.slice(0, 16).replace('T', ' ');
    }
    return text;
  }

  function formatDateObject(date) {
    if (!date || isNaN(date.getTime())) {
      return '\u2014';
    }
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
      + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
  }

  function licenseExpiry(item) {
    if (!item || !item.activatedAt) {
      return '\u2014';
    }
    if (!item.validDays || item.validDays <= 0) {
      return '永久';
    }
    var date = new Date(item.activatedAt);
    if (isNaN(date.getTime())) {
      return '\u2014';
    }
    date.setDate(date.getDate() + Number(item.validDays));
    return formatDateObject(date);
  }

  function formatDays(value) {
    if (value === null || value === undefined || value === '' || Number(value) <= 0) {
      return '永久';
    }
    return value + ' 天';
  }

  function statusMeta(status) {
    if (Number(status) === 0) {
      return { label: '未使用', className: 'status-unused' };
    }
    if (Number(status) === 1) {
      return { label: '已激活', className: 'status-activated' };
    }
    if (Number(status) === 2) {
      return { label: '已禁用', className: 'status-disabled' };
    }
    return { label: '未知', className: 'status-unknown' };
  }

  function initials(user) {
    var name = (user && (user.realName || user.username)) || 'A';
    return name.trim().slice(0, 1).toUpperCase();
  }

  function shortText(value, length) {
    if (!value) {
      return '\u2014';
    }
    if (value.length <= length) {
      return value;
    }
    return value.slice(0, length) + '\u2026';
  }

  function escapeHtml(value) {
    return String(value === null || value === undefined ? '' : value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  window.ATW = {
    KEYS: KEYS,
    getToken: getToken,
    getUser: getUser,
    saveUser: saveUser,
    setAuth: setAuth,
    clearAuth: clearAuth,
    loginUrl: loginUrl,
    redirectToLogin: redirectToLogin,
    api: api,
    setButtonLoading: setButtonLoading,
    copyText: copyText,
    toast: toast,
    formatDateTime: formatDateTime,
    formatDays: formatDays,
    licenseExpiry: licenseExpiry,
    statusMeta: statusMeta,
    initials: initials,
    shortText: shortText,
    escapeHtml: escapeHtml
  };
})();

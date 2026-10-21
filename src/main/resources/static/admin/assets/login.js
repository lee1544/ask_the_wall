(function () {
  'use strict';

  var ATW = window.ATW;

  document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('loginForm');
    var usernameInput = document.getElementById('username');
    var passwordInput = document.getElementById('password');
    var rememberInput = document.getElementById('rememberUsername');
    var loginButton = document.getElementById('loginBtn');
    var toggleButton = document.getElementById('togglePassword');
    var alertBox = document.getElementById('loginAlert');
    var loading = document.getElementById('loginLoading');

    var remembered = localStorage.getItem(ATW.KEYS.username) || '';
    if (remembered) {
      usernameInput.value = remembered;
      rememberInput.checked = true;
    }

    var reason = new URLSearchParams(window.location.search).get('reason');
    if (reason === 'expired') {
      showAlert('登录状态已失效，请重新登录。', 'warning');
    }

    restoreSession();

    form.addEventListener('submit', onSubmit);
    toggleButton.addEventListener('click', togglePassword);

    function showAlert(message, type) {
      alertBox.textContent = message;
      alertBox.className = 'alert alert-' + (type || 'error');
      alertBox.hidden = false;
    }

    function hideAlert() {
      alertBox.hidden = true;
      alertBox.textContent = '';
    }

    function togglePassword() {
      var visible = passwordInput.type === 'text';
      passwordInput.type = visible ? 'password' : 'text';
      toggleButton.classList.toggle('is-visible', !visible);
      toggleButton.setAttribute('aria-label', visible ? '显示密码' : '隐藏密码');
      toggleButton.setAttribute('title', visible ? '显示密码' : '隐藏密码');
      passwordInput.focus();
    }

    async function restoreSession() {
      if (!ATW.getToken()) {
        focusFirstField();
        return;
      }
      loading.hidden = false;
      try {
        var user = await ATW.api('/api/auth/userinfo', { timeout: 8000 });
        ATW.saveUser(user);
        window.location.replace('index.html');
        return;
      } catch (e) {
        if (e.status === 401) {
          ATW.clearAuth();
        }
      } finally {
        loading.hidden = true;
      }
      focusFirstField();
    }

    function focusFirstField() {
      if (remembered) {
        passwordInput.focus();
      } else {
        usernameInput.focus();
      }
    }

    async function onSubmit(event) {
      event.preventDefault();
      hideAlert();

      var username = usernameInput.value.trim();
      var password = passwordInput.value;
      if (!username) {
        showAlert('请输入用户名。', 'error');
        usernameInput.focus();
        return;
      }
      if (!password) {
        showAlert('请输入密码。', 'error');
        passwordInput.focus();
        return;
      }

      ATW.setButtonLoading(loginButton, true, '登录中...');
      try {
        var result = await ATW.api('/api/auth/login', {
          method: 'POST',
          body: { username: username, password: password },
          auth: false,
          redirectOn401: false
        });
        if (!result || !result.token) {
          throw new Error('登录响应异常，请稍后重试');
        }
        ATW.setAuth(result.token, result.userInfo);
        if (rememberInput.checked) {
          localStorage.setItem(ATW.KEYS.username, username);
        } else {
          localStorage.removeItem(ATW.KEYS.username);
        }
        window.location.replace('index.html');
      } catch (e) {
        ATW.setButtonLoading(loginButton, false);
        showAlert(e.message || '登录失败，请稍后重试。', 'error');
        passwordInput.select();
      }
    }
  });
})();

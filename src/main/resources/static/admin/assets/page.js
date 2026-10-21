(function () {
  'use strict';

  var ATW = window.ATW;
  var currentUser = null;

  function el(id) {
    return document.getElementById(id);
  }

  function permissionsOf(user) {
    return user && Array.isArray(user.permissions) ? user.permissions : [];
  }

  function requiredList(value) {
    if (!value) {
      return [];
    }
    if (Array.isArray(value)) {
      return value;
    }
    return String(value).split(/[\s,]+/).filter(function (item) {
      return item.length > 0;
    });
  }

  function can(required) {
    var list = requiredList(required);
    if (list.length === 0) {
      return true;
    }
    var permissions = permissionsOf(currentUser);
    return list.some(function (permission) {
      return permissions.indexOf(permission) !== -1;
    });
  }

  function isSuperAdmin() {
    var roles = currentUser && Array.isArray(currentUser.roles) ? currentUser.roles : [];
    return roles.indexOf('SUPER_ADMIN') !== -1;
  }

  function applyUser(user) {
    currentUser = user;
    var name = user.realName || user.username || '-';
    var org = user.orgName || (Array.isArray(user.roles) ? user.roles.join(' / ') : '') || '-';
    var avatar = ATW.initials(user);
    var pairs = [
      ['userAvatar', avatar],
      ['topbarAvatar', avatar],
      ['sidebarUserName', name],
      ['sidebarUserOrg', org],
      ['topbarUserName', name],
      ['topbarUserOrg', org]
    ];
    pairs.forEach(function (pair) {
      var node = el(pair[0]);
      if (node) {
        node.textContent = pair[1];
      }
    });
    if (el('userAvatar')) {
      el('userAvatar').hidden = false;
    }
  }

  function applyPermissionVisibility(root) {
    var scope = root || document;
    var nodes = scope.querySelectorAll('[data-any-permission]');
    Array.prototype.forEach.call(nodes, function (node) {
      var allowed = can(node.getAttribute('data-any-permission'));
      node.hidden = !allowed;
    });
  }

  function applyPageAccess(permission) {
    var allowed = !permission || can(permission);
    var denied = el('pageDenied');
    var content = el('pageContent');
    if (denied) {
      denied.hidden = allowed;
    }
    if (content) {
      content.hidden = !allowed;
    }
    return allowed;
  }

  function toggleSidebar() {
    var sidebar = el('sidebar');
    var scrim = el('sidebarScrim');
    if (!sidebar || !scrim) {
      return;
    }
    var open = !sidebar.classList.contains('is-open');
    sidebar.classList.toggle('is-open', open);
    document.body.classList.toggle('nav-open', open);
    scrim.hidden = !open;
  }

  function closeSidebar() {
    var sidebar = el('sidebar');
    var scrim = el('sidebarScrim');
    if (sidebar) {
      sidebar.classList.remove('is-open');
    }
    if (scrim) {
      scrim.hidden = true;
    }
    document.body.classList.remove('nav-open');
  }

  async function logout() {
    try {
      await ATW.api('/api/auth/logout', {
        method: 'POST',
        timeout: 5000,
        redirectOn401: false
      });
    } catch (e) {
    }
    ATW.clearAuth();
    ATW.redirectToLogin();
  }

  function bindChrome() {
    var menuBtn = el('menuBtn');
    var scrim = el('sidebarScrim');
    if (menuBtn) {
      menuBtn.addEventListener('click', toggleSidebar);
    }
    if (scrim) {
      scrim.addEventListener('click', closeSidebar);
    }
    Array.prototype.forEach.call(document.querySelectorAll('[data-logout]'), function (button) {
      button.addEventListener('click', logout);
    });
    Array.prototype.forEach.call(document.querySelectorAll('.sidebar-nav .nav-item'), function (link) {
      link.addEventListener('click', closeSidebar);
    });
  }

  function refreshUserInfo(pagePermission) {
    return ATW.api('/api/auth/userinfo', { timeout: 10000 }).then(function (fresh) {
      if (!fresh) {
        return null;
      }
      ATW.saveUser(fresh);
      applyUser(fresh);
      applyPermissionVisibility(document);
      applyPageAccess(pagePermission);
      return fresh;
    }, function (error) {
      if (error.status !== 401) {
        ATW.toast(error.message || '无法获取账号信息', 'error');
      }
      return null;
    });
  }

  async function init(options) {
    var config = options || {};
    if (!ATW.getToken()) {
      ATW.redirectToLogin();
      return null;
    }

    bindChrome();

    var user = ATW.getUser();
    if (user && (!config.permission || can(config.permission))) {
      applyUser(user);
      applyPermissionVisibility(document);
      applyPageAccess(config.permission);
      refreshUserInfo(config.permission);
      return user;
    }

    var fresh = await refreshUserInfo(config.permission);
    if (fresh) {
      user = fresh;
    }

    if (!user) {
      ATW.clearAuth();
      ATW.redirectToLogin('expired');
      return null;
    }

    applyUser(user);
    applyPermissionVisibility(document);
    applyPageAccess(config.permission);
    return user;
  }

  function confirmAction(options) {
    var config = options || {};
    return new Promise(function (resolve) {
      var modal = document.createElement('div');
      modal.className = 'modal is-open confirm-modal';
      modal.innerHTML = ''
        + '<div class="modal-card confirm-card" role="dialog" aria-modal="true" aria-labelledby="confirmTitle">'
        + '  <div class="modal-head">'
        + '    <div>'
        + '      <p class="eyebrow">CONFIRM</p>'
        + '      <h2 id="confirmTitle"></h2>'
        + '    </div>'
        + '  </div>'
        + '  <p class="confirm-message"></p>'
        + '  <div class="modal-foot">'
        + '    <button class="btn btn-ghost" type="button" data-confirm-cancel>取消</button>'
        + '    <button class="btn ' + (config.danger ? 'btn-danger' : 'btn-primary') + '" type="button" data-confirm-ok></button>'
        + '  </div>'
        + '</div>';

      modal.querySelector('h2').textContent = config.title || '请确认操作';
      modal.querySelector('.confirm-message').textContent = config.message || '';
      var ok = modal.querySelector('[data-confirm-ok]');
      ok.textContent = config.confirmText || '确认';
      document.body.appendChild(modal);

      function close(result) {
        if (modal.parentNode) {
          modal.parentNode.removeChild(modal);
        }
        document.removeEventListener('keydown', onKey);
        resolve(result);
      }

      function onKey(event) {
        if (event.key === 'Escape') {
          close(false);
        }
      }

      ok.addEventListener('click', function () {
        close(true);
      });
      modal.querySelector('[data-confirm-cancel]').addEventListener('click', function () {
        close(false);
      });
      modal.addEventListener('click', function (event) {
        if (event.target === modal) {
          close(false);
        }
      });
      document.addEventListener('keydown', onKey);
      ok.focus();
    });
  }

  function openModal(id) {
    var modal = el(id);
    if (!modal) {
      return;
    }
    modal.hidden = false;
    requestAnimationFrame(function () {
      modal.classList.add('is-open');
    });
    var focusable = modal.querySelector('input:not([type="hidden"]), select, textarea, button');
    if (focusable) {
      setTimeout(function () {
        focusable.focus();
      }, 80);
    }
  }

  function closeModal(id) {
    var modal = el(id);
    if (!modal) {
      return;
    }
    modal.classList.remove('is-open');
    setTimeout(function () {
      modal.hidden = true;
    }, 180);
  }

  function bindModalClose() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-close]'), function (button) {
      button.addEventListener('click', function () {
        closeModal(button.getAttribute('data-close'));
      });
    });
    Array.prototype.forEach.call(document.querySelectorAll('.modal'), function (modal) {
      modal.addEventListener('click', function (event) {
        if (event.target === modal) {
          closeModal(modal.id);
        }
      });
    });
    document.addEventListener('keydown', function (event) {
      if (event.key !== 'Escape') {
        return;
      }
      var open = document.querySelector('.modal.is-open[data-static-close]');
      if (open) {
        closeModal(open.id);
      }
    });
  }

  window.AdminPage = {
    init: init,
    can: can,
    isSuperAdmin: isSuperAdmin,
    user: function () {
      return currentUser;
    },
    confirm: confirmAction,
    openModal: openModal,
    closeModal: closeModal,
    bindModalClose: bindModalClose,
    escape: ATW.escapeHtml
  };
})();

(function () {
  'use strict';

  var ATW = window.ATW;
  var AdminPage = window.AdminPage;

  var state = {
    page: 0,
    size: 10,
    realname: '',
    status: '',
    items: [],
    pageData: null,
    roles: [],
    roleMap: {},
    editing: null,
    roleUser: null
  };

  var canRoleView = false;
  var currentUserId = null;

  function el(id) {
    return document.getElementById(id);
  }

  document.addEventListener('DOMContentLoaded', function () {
    AdminPage.init({ permission: 'user:view' }).then(function (user) {
      if (!user || !AdminPage.can('user:view')) {
        return;
      }
      currentUserId = user.id;
      canRoleView = AdminPage.can('role:view');
      init();
    });
  });

  function init() {
    AdminPage.bindModalClose();
    bindEvents();
    if (canRoleView) {
      loadRoles();
    }
    loadList();
  }

  function bindEvents() {
    el('filterForm').addEventListener('submit', function (event) {
      event.preventDefault();
      state.realname = el('filterRealname').value.trim();
      state.status = el('statusFilter').value;
      state.page = 0;
      loadList();
    });
    el('resetFilterBtn').addEventListener('click', function () {
      el('filterRealname').value = '';
      el('statusFilter').value = '';
      state.realname = '';
      state.status = '';
      state.page = 0;
      loadList();
    });
    el('refreshBtn').addEventListener('click', function () {
      if (canRoleView) {
        loadRoles();
      }
      loadList();
    });
    el('createBtn').addEventListener('click', openCreate);
    el('userForm').addEventListener('submit', submitUser);
    el('saveUserRolesBtn').addEventListener('click', saveUserRoles);
    el('pageSize').addEventListener('change', function () {
      state.size = Number(el('pageSize').value) || 10;
      state.page = 0;
      loadList();
    });
    el('firstPageBtn').addEventListener('click', function () {
      goPage(0);
    });
    el('prevPageBtn').addEventListener('click', function () {
      goPage(state.page - 1);
    });
    el('nextPageBtn').addEventListener('click', function () {
      goPage(state.page + 1);
    });
    el('lastPageBtn').addEventListener('click', function () {
      goPage(state.pageData ? state.pageData.totalPages - 1 : 0);
    });
    el('userTableBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-action]');
      if (!button || button.disabled) {
        return;
      }
      var id = Number(button.dataset.id);
      var user = state.items.filter(function (item) {
        return item.id === id;
      })[0];
      if (!user) {
        return;
      }
      if (button.dataset.action === 'edit') {
        openEdit(user);
      } else if (button.dataset.action === 'toggle') {
        toggleStatus(user);
      } else if (button.dataset.action === 'roles') {
        openRoles(user);
      } else if (button.dataset.action === 'reset-password') {
        resetPassword(user);
      } else if (button.dataset.action === 'delete') {
        removeUser(user);
      }
    });
  }

  async function loadRoles() {
    try {
      var roles = await ATW.api('/api/roles');
      state.roles = Array.isArray(roles) ? roles : [];
    } catch (e) {
      state.roles = [];
    }
  }

  async function loadList() {
    el('listSummary').textContent = '加载中...';
    var query = new URLSearchParams();
    query.set('page', String(state.page));
    query.set('size', String(state.size));
    if (state.realname) {
      query.set('realname', state.realname);
    }
    if (state.status !== '') {
      query.set('status', state.status);
    }

    try {
      var result = await ATW.api('/api/users?' + query.toString());
      state.pageData = result || { items: [], totalElements: 0, totalPages: 0 };
      state.items = Array.isArray(result.items) ? result.items : [];
      if (state.items.length === 0 && state.page > 0 && state.pageData.totalElements > 0) {
        state.page -= 1;
        return loadList();
      }
      if (canRoleView && state.items.length > 0) {
        await loadUserRoleIds();
      }
      renderTable();
      renderPager();
    } catch (e) {
      state.items = [];
      state.pageData = null;
      renderError(e.message);
      renderPager();
    }
  }

  async function loadUserRoleIds() {
    var map = {};
    await Promise.all(state.items.map(async function (user) {
      try {
        var ids = await ATW.api('/api/users/' + user.id + '/roles');
        map[user.id] = Array.isArray(ids) ? ids : [];
      } catch (e) {
        map[user.id] = [];
      }
    }));
    state.roleMap = map;
  }

  function renderTable() {
    var body = el('userTableBody');
    if (state.items.length === 0) {
      body.innerHTML = '<tr><td colspan="7"><div class="table-state table-empty"><strong>暂无用户</strong><span>可以点击右上角“新建用户”添加账号。</span></div></td></tr>';
      el('listSummary').textContent = '共 0 个用户';
      return;
    }

    var canUpdate = AdminPage.can('user:update');
    var canAssign = AdminPage.can('user:assign-role') && canRoleView;
    var canReset = AdminPage.can('user:reset-password');
    var canDelete = AdminPage.can('user:delete');

    body.innerHTML = state.items.map(function (user) {
      var isSelf = Number(user.id) === Number(currentUserId);
      var actions = '';
      if (canUpdate) {
        actions += '<button class="mini-btn" type="button" data-action="edit" data-id="' + user.id + '">编辑</button>';
        actions += '<button class="mini-btn" type="button" data-action="toggle" data-id="' + user.id + '"'
          + (isSelf ? ' disabled title="不能禁用当前登录账号"' : '') + '>'
          + (Number(user.status) === 1 ? '禁用' : '启用') + '</button>';
      }
      if (canAssign) {
        actions += '<button class="mini-btn" type="button" data-action="roles" data-id="' + user.id + '"'
          + (isSelf ? ' disabled title="不能修改当前登录账号的角色"' : '') + '>角色</button>';
      }
      if (canReset) {
        actions += '<button class="mini-btn" type="button" data-action="reset-password" data-id="' + user.id + '"'
          + (isSelf ? ' disabled title="不能重置当前登录账号的密码"' : '') + '>重置密码</button>';
      }
      if (canDelete) {
        actions += '<button class="mini-btn is-danger" type="button" data-action="delete" data-id="' + user.id + '"'
          + (isSelf ? ' disabled title="不能删除当前登录账号"' : '') + '>删除</button>';
      }
      if (!actions) {
        actions = '<span class="muted-value">只读</span>';
      }

      return '<tr>'
        + '<td><span class="code-pill">' + ATW.escapeHtml(user.username) + '</span>'
        + (isSelf ? ' <span class="self-tag">当前</span>' : '') + '</td>'
        + '<td><strong>' + ATW.escapeHtml(user.realName || '\u2014') + '</strong></td>'
        + '<td>' + renderRoleBadges(user) + '</td>'
        + '<td>' + statusBadge(user.status) + '</td>'
        + '<td class="muted-value">' + ATW.formatDateTime(user.lastLoginTime) + '</td>'
        + '<td class="muted-value">' + ATW.formatDateTime(user.createTime) + '</td>'
        + '<td class="td-actions"><div class="row-actions">' + actions + '</div></td>'
        + '</tr>';
    }).join('');

    el('listSummary').textContent = '共 ' + state.pageData.totalElements + ' 个用户'
      + (state.realname ? '，姓名：' + state.realname : '');
  }

  function renderRoleBadges(user) {
    if (!canRoleView) {
      return '<span class="muted-value">需要 role:view</span>';
    }
    var ids = state.roleMap[user.id] || [];
    if (ids.length === 0) {
      return '<span class="muted-value">未分配</span>';
    }
    var roles = ids.map(function (id) {
      return state.roles.filter(function (role) {
        return Number(role.id) === Number(id);
      })[0];
    }).filter(Boolean);
    if (roles.length === 0) {
      return '<span class="muted-value">未分配</span>';
    }
    var visible = roles.slice(0, 2).map(function (role) {
      return '<span class="role-badge">' + ATW.escapeHtml(role.roleName) + '</span>';
    }).join('');
    if (roles.length > 2) {
      visible += '<span class="role-badge role-badge-more">+' + (roles.length - 2) + '</span>';
    }
    return '<span class="badge-list">' + visible + '</span>';
  }

  function statusBadge(status) {
    var enabled = Number(status) === 1;
    return '<span class="status-badge ' + (enabled ? 'status-activated' : 'status-disabled') + '">'
      + (enabled ? '启用' : '禁用') + '</span>';
  }

  function renderError(message) {
    el('userTableBody').innerHTML = '<tr><td colspan="7"><div class="table-state table-empty"><strong>加载失败</strong><span>'
      + ATW.escapeHtml(message) + '</span><button class="btn btn-ghost btn-sm" type="button" onclick="location.reload()">重新加载</button></div></td></tr>';
    el('listSummary').textContent = '加载失败';
  }

  function renderPager() {
    var data = state.pageData || { totalElements: 0, totalPages: 0 };
    var totalPages = data.totalPages || 0;
    el('pageInfo').textContent = '共 ' + data.totalElements + ' 个用户';
    el('pageNumber').textContent = totalPages === 0 ? '0 / 0' : (state.page + 1) + ' / ' + totalPages;
    el('firstPageBtn').disabled = state.page <= 0;
    el('prevPageBtn').disabled = state.page <= 0;
    el('nextPageBtn').disabled = totalPages === 0 || state.page >= totalPages - 1;
    el('lastPageBtn').disabled = totalPages === 0 || state.page >= totalPages - 1;
  }

  function goPage(page) {
    var totalPages = state.pageData ? state.pageData.totalPages : 0;
    if (page < 0 || (totalPages > 0 && page >= totalPages) || (!state.pageData && page > 0)) {
      return;
    }
    state.page = page;
    loadList();
  }

  function openCreate() {
    state.editing = null;
    el('userForm').reset();
    el('userId').value = '';
    el('usernameField').hidden = false;
    el('passwordField').hidden = false;
    el('realNameField').classList.remove('field-full');
    el('userModalTitle').textContent = '新建用户';
    el('userModalHint').textContent = '新用户创建后不会自动分配角色，请在用户列表中为其分配角色。';
    el('userSubmitBtn').textContent = '创建用户';
    AdminPage.openModal('userModal');
  }

  function openEdit(user) {
    state.editing = user;
    el('userForm').reset();
    el('userId').value = String(user.id);
    el('username').value = user.username || '';
    el('realName').value = user.realName || '';
    el('usernameField').hidden = true;
    el('passwordField').hidden = true;
    el('realNameField').classList.add('field-full');
    el('userModalTitle').textContent = '编辑用户';
    el('userModalHint').textContent = '用户名和密码不能在这里修改，请使用“重置密码”操作。';
    el('userSubmitBtn').textContent = '保存修改';
    AdminPage.openModal('userModal');
  }

  async function submitUser(event) {
    event.preventDefault();
    var id = el('userId').value;
    var realName = el('realName').value.trim();
    if (!realName) {
      ATW.toast('请输入姓名', 'error');
      el('realName').focus();
      return;
    }

    var body;
    if (id) {
      body = { realName: realName };
    } else {
      var username = el('username').value.trim();
      var password = el('password').value;
      if (username.length < 6) {
        ATW.toast('用户名至少 6 个字符', 'error');
        el('username').focus();
        return;
      }
      if (password.length < 6) {
        ATW.toast('密码至少 6 个字符', 'error');
        el('password').focus();
        return;
      }
      body = { username: username, password: password, realName: realName };
    }

    var button = el('userSubmitBtn');
    ATW.setButtonLoading(button, true, id ? '保存中...' : '创建中...');
    try {
      if (id) {
        await ATW.api('/api/users/' + id, { method: 'PUT', body: body });
        ATW.toast('用户信息已更新', 'success');
      } else {
        await ATW.api('/api/users', { method: 'POST', body: body });
        ATW.toast('用户已创建', 'success');
      }
      AdminPage.closeModal('userModal');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '保存失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function openRoles(user) {
    state.roleUser = user;
    el('userRolesTitle').textContent = '分配角色';
    el('userRolesHint').textContent = '为“' + (user.realName || user.username) + '”分配角色，保存后会替换当前所有角色。';
    el('userRoleOptions').innerHTML = '<div class="loading-line"><span class="spinner"></span>加载角色...</div>';
    AdminPage.openModal('userRolesModal');

    if (state.roles.length === 0) {
      await loadRoles();
    }

    var current = state.roleMap[user.id];
    if (!current) {
      try {
        current = await ATW.api('/api/users/' + user.id + '/roles');
      } catch (e) {
        current = [];
      }
    }
    var selected = {};
    (Array.isArray(current) ? current : []).forEach(function (id) {
      selected[id] = true;
    });

    if (state.roles.length === 0) {
      el('userRoleOptions').innerHTML = '<div class="empty-inline">当前组织没有可分配的角色。</div>';
      return;
    }

    el('userRoleOptions').innerHTML = state.roles.map(function (role) {
      return '<label class="check-item">'
        + '<input type="checkbox" value="' + role.id + '"' + (selected[role.id] ? ' checked' : '') + '>'
        + '<span class="check-item-body">'
        + '<span class="check-item-title">' + ATW.escapeHtml(role.roleName)
        + ' <span class="code-pill code-pill-sm">' + ATW.escapeHtml(role.roleCode) + '</span></span>'
        + '<span class="check-item-desc">' + ATW.escapeHtml(role.description || '无描述') + '</span>'
        + '</span>'
        + '</label>';
    }).join('');
  }

  async function saveUserRoles() {
    if (!state.roleUser) {
      return;
    }
    var checked = Array.prototype.map.call(
      el('userRoleOptions').querySelectorAll('input[type="checkbox"]:checked'),
      function (input) {
        return Number(input.value);
      }
    );
    var button = el('saveUserRolesBtn');
    ATW.setButtonLoading(button, true, '保存中...');
    try {
      await ATW.api('/api/users/' + state.roleUser.id + '/roles', {
        method: 'POST',
        body: checked
      });
      state.roleMap[state.roleUser.id] = checked;
      ATW.toast('角色已更新', 'success');
      AdminPage.closeModal('userRolesModal');
      renderTable();
    } catch (e) {
      ATW.toast(e.message || '保存角色失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function toggleStatus(user) {
    var next = Number(user.status) === 1 ? 0 : 1;
    var confirmed = await AdminPage.confirm({
      title: next === 0 ? '禁用用户' : '启用用户',
      message: next === 0
        ? '禁用后“' + (user.realName || user.username) + '”将无法登录，确定继续吗？'
        : '启用后该用户可以重新登录，确定继续吗？',
      confirmText: next === 0 ? '禁用' : '启用',
      danger: next === 0
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/users/' + user.id + '/status?status=' + next, { method: 'PUT' });
      ATW.toast(next === 1 ? '用户已启用' : '用户已禁用', 'success');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '操作失败', 'error');
    }
  }

  async function resetPassword(user) {
    var confirmed = await AdminPage.confirm({
      title: '重置密码',
      message: '确定把“' + (user.realName || user.username) + '”的密码重置为初始密码 12345678 吗？',
      confirmText: '重置密码'
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/users/' + user.id + '/reset-password', { method: 'PUT' });
      ATW.toast('密码已重置为 12345678', 'success');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '重置密码失败', 'error');
    }
  }

  async function removeUser(user) {
    var confirmed = await AdminPage.confirm({
      title: '删除用户',
      message: '确定删除“' + (user.realName || user.username) + '”吗？该操作无法恢复。',
      confirmText: '删除用户',
      danger: true
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/users/' + user.id, { method: 'DELETE' });
      ATW.toast('用户已删除', 'success');
      if (state.items.length === 1 && state.page > 0) {
        state.page -= 1;
      }
      loadList();
    } catch (e) {
      ATW.toast(e.message || '删除失败', 'error');
    }
  }
})();

(function () {
  'use strict';

  var ATW = window.ATW;
  var AdminPage = window.AdminPage;

  var state = {
    permissions: [],
    roles: [],
    keyword: '',
    status: ''
  };

  var CATEGORY_LABELS = {
    user: '用户',
    org: '组织',
    role: '角色',
    license: 'License',
    permission: '权限'
  };

  function el(id) {
    return document.getElementById(id);
  }

  document.addEventListener('DOMContentLoaded', function () {
    AdminPage.init({ permission: 'role:view' }).then(function (user) {
      if (!user || !AdminPage.can('role:view')) {
        return;
      }
      init();
    });
  });

  function init() {
    bindEvents();
    loadAll();
  }

  function bindEvents() {
    el('filterForm').addEventListener('submit', function (event) {
      event.preventDefault();
      state.keyword = el('keyword').value.trim().toLowerCase();
      state.status = el('statusFilter').value;
      renderTable();
    });
    el('resetFilterBtn').addEventListener('click', function () {
      el('keyword').value = '';
      el('statusFilter').value = '';
      state.keyword = '';
      state.status = '';
      renderTable();
    });
    el('refreshBtn').addEventListener('click', loadAll);
    el('permissionTableBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-action]');
      if (!button || button.disabled) {
        return;
      }
      var id = Number(button.dataset.id);
      var permission = state.permissions.filter(function (item) {
        return Number(item.id) === id;
      })[0];
      if (permission) {
        toggleStatus(permission);
      }
    });
  }

  async function loadAll() {
    el('listSummary').textContent = '加载中...';
    try {
      var permissions = await ATW.api('/api/permissions');
      state.permissions = Array.isArray(permissions) ? permissions : [];
    } catch (e) {
      state.permissions = [];
      renderError(e.message);
      return;
    }
    try {
      var roles = await ATW.api('/api/roles');
      state.roles = Array.isArray(roles) ? roles : [];
    } catch (e) {
      state.roles = [];
    }
    renderTable();
  }

  function categoryOf(code) {
    var prefix = (code || '').split(':')[0];
    return CATEGORY_LABELS[prefix] || '其他';
  }

  function usageOf(permissionId) {
    return state.roles.filter(function (role) {
      var permissions = Array.isArray(role.permissions) ? role.permissions : [];
      return permissions.some(function (permission) {
        return Number(permission.id) === Number(permissionId);
      });
    });
  }

  function filteredPermissions() {
    return state.permissions.filter(function (permission) {
      if (state.status !== '' && String(permission.status) !== state.status) {
        return false;
      }
      if (!state.keyword) {
        return true;
      }
      var text = ((permission.permissionCode || '') + ' ' + (permission.permissionName || '')).toLowerCase();
      return text.indexOf(state.keyword) !== -1;
    });
  }

  function renderTable() {
    var body = el('permissionTableBody');
    var items = filteredPermissions();
    var canUpdate = AdminPage.can('role:update') && AdminPage.isSuperAdmin();
    var enabledCount = state.permissions.filter(function (permission) {
      return Number(permission.status) === 1;
    }).length;

    el('listSummary').textContent = '共 ' + state.permissions.length + ' 个权限，' + enabledCount + ' 个启用'
      + (state.keyword || state.status !== '' ? '，当前筛选 ' + items.length + ' 个' : '');
    el('pageInfo').textContent = '共 ' + state.permissions.length + ' 个权限';

    if (items.length === 0) {
      body.innerHTML = '<tr><td colspan="7"><div class="table-state table-empty"><strong>没有匹配的权限</strong><span>请调整关键字或状态筛选。</span></div></td></tr>';
      return;
    }

    body.innerHTML = items.map(function (permission) {
      var roles = usageOf(permission.id);
      var roleNames = roles.map(function (role) {
        return role.roleName;
      }).join('、');
      var usage = roles.length > 0
        ? '<span class="count-pill" title="' + ATW.escapeHtml(roleNames) + '">' + roles.length + ' 个角色</span>'
        : '<span class="muted-value">未分配</span>';
      var action = canUpdate
        ? '<button class="mini-btn' + (Number(permission.status) === 1 ? ' is-danger' : '') + '" type="button" data-action="toggle" data-id="'
          + permission.id + '">' + (Number(permission.status) === 1 ? '禁用' : '启用') + '</button>'
        : '<span class="muted-value">只读</span>';

      return '<tr>'
        + '<td><span class="code-pill">' + ATW.escapeHtml(permission.permissionCode) + '</span></td>'
        + '<td><strong>' + ATW.escapeHtml(permission.permissionName) + '</strong></td>'
        + '<td><span class="role-badge">' + ATW.escapeHtml(categoryOf(permission.permissionCode)) + '</span></td>'
        + '<td>' + usage + '</td>'
        + '<td>' + statusBadge(permission.status) + '</td>'
        + '<td class="muted-value">' + ATW.formatDateTime(permission.updateTime) + '</td>'
        + '<td class="td-actions"><div class="row-actions">' + action + '</div></td>'
        + '</tr>';
    }).join('');
  }

  function statusBadge(status) {
    var enabled = Number(status) === 1;
    return '<span class="status-badge ' + (enabled ? 'status-activated' : 'status-disabled') + '">'
      + (enabled ? '启用' : '禁用') + '</span>';
  }

  function renderError(message) {
    el('permissionTableBody').innerHTML = '<tr><td colspan="7"><div class="table-state table-empty"><strong>加载失败</strong><span>'
      + ATW.escapeHtml(message) + '</span><button class="btn btn-ghost btn-sm" type="button" onclick="location.reload()">重新加载</button></div></td></tr>';
    el('listSummary').textContent = '加载失败';
    el('pageInfo').textContent = '加载失败';
  }

  async function toggleStatus(permission) {
    var next = Number(permission.status) === 1 ? 0 : 1;
    var confirmed = await AdminPage.confirm({
      title: next === 0 ? '禁用权限' : '启用权限',
      message: next === 0
        ? '禁用“' + permission.permissionName + '”后，所有角色和用户将立即失去该权限，确定继续吗？'
        : '启用“' + permission.permissionName + '”后，已分配该权限的角色会立即恢复使用，确定继续吗？',
      confirmText: next === 0 ? '禁用权限' : '启用权限',
      danger: next === 0
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/permissions/' + permission.id + '/status?status=' + next, { method: 'PUT' });
      ATW.toast(next === 1 ? '权限已启用' : '权限已禁用', 'success');
      loadAll();
    } catch (e) {
      ATW.toast(e.message || '操作失败', 'error');
    }
  }
})();

(function () {
  'use strict';

  var ATW = window.ATW;
  var AdminPage = window.AdminPage;

  var state = {
    roles: [],
    permissions: [],
    editing: null,
    permissionRole: null
  };

  var GROUP_LABELS = {
    user: '用户权限',
    org: '组织权限',
    role: '角色权限',
    license: 'License 权限',
    permission: '权限管理',
    other: '其他权限'
  };

  var GROUP_ORDER = ['user', 'org', 'role', 'license', 'permission', 'other'];

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
    AdminPage.bindModalClose();
    bindEvents();
    loadAll();
  }

  function bindEvents() {
    el('refreshBtn').addEventListener('click', loadAll);
    el('createBtn').addEventListener('click', openCreate);
    el('roleForm').addEventListener('submit', submitRole);
    el('permissionSearch').addEventListener('input', filterPermissions);
    el('selectAllPermissionsBtn').addEventListener('click', function () {
      setVisiblePermissions(true);
    });
    el('clearAllPermissionsBtn').addEventListener('click', function () {
      setVisiblePermissions(false);
    });
    el('saveRolePermissionsBtn').addEventListener('click', saveRolePermissions);
    el('permissionGroups').addEventListener('change', function (event) {
      if (event.target.matches('input[type="checkbox"]')) {
        updateSelectedCount();
      }
    });
    el('roleTableBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-action]');
      if (!button || button.disabled) {
        return;
      }
      var id = Number(button.dataset.id);
      var role = state.roles.filter(function (item) {
        return Number(item.id) === id;
      })[0];
      if (!role) {
        return;
      }
      if (button.dataset.action === 'edit') {
        openEdit(role);
      } else if (button.dataset.action === 'permissions') {
        openPermissions(role);
      } else if (button.dataset.action === 'delete') {
        removeRole(role);
      }
    });
  }

  async function loadAll() {
    el('listSummary').textContent = '加载中...';
    var rolesRequest = ATW.api('/api/roles').then(
      function (data) {
        return { data: data };
      },
      function (error) {
        return { error: error };
      }
    );
    var permissionsRequest = ATW.api('/api/permissions').then(
      function (data) {
        return { data: data };
      },
      function (error) {
        return { error: error };
      }
    );
    var results = await Promise.all([rolesRequest, permissionsRequest]);

    var rolesResult = results[0];
    var rolesOk = !rolesResult.error;
    state.roles = rolesOk && Array.isArray(rolesResult.data) ? rolesResult.data : [];
    if (!rolesOk) {
      renderError(rolesResult.error.message);
    }

    var permissionsResult = results[1];
    state.permissions = !permissionsResult.error && Array.isArray(permissionsResult.data)
      ? permissionsResult.data
      : [];
    if (permissionsResult.error && rolesOk) {
      ATW.toast('权限列表加载失败：' + permissionsResult.error.message, 'error');
    }

    if (rolesOk) {
      renderTable();
    }
  }

  function isSystemRole(role) {
    var code = role.roleCode || '';
    return code === 'SUPER_ADMIN'
      || code.indexOf('ORG_ADMIN_') === 0
      || code.indexOf('NORMAL_ADMIN_') === 0;
  }

  function renderTable() {
    var body = el('roleTableBody');
    if (state.roles.length === 0) {
      body.innerHTML = '<tr><td colspan="7"><div class="table-state table-empty"><strong>暂无角色</strong><span>当前组织还没有可用角色。</span></div></td></tr>';
      el('listSummary').textContent = '共 0 个角色';
      el('pageInfo').textContent = '共 0 个角色';
      return;
    }

    var canUpdate = AdminPage.can('role:update');
    var canAssign = AdminPage.can('role:assign-permission');
    var canDelete = AdminPage.can('role:delete');

    body.innerHTML = state.roles.map(function (role) {
      var permissions = Array.isArray(role.permissions) ? role.permissions : [];
      var actions = '';
      if (canAssign) {
        actions += '<button class="mini-btn" type="button" data-action="permissions" data-id="' + role.id + '">权限</button>';
      }
      if (canUpdate) {
        actions += '<button class="mini-btn" type="button" data-action="edit" data-id="' + role.id + '">编辑</button>';
      }
      if (canDelete && !isSystemRole(role)) {
        actions += '<button class="mini-btn is-danger" type="button" data-action="delete" data-id="' + role.id + '">删除</button>';
      }
      if (!actions) {
        actions = '<span class="muted-value">只读</span>';
      }

      return '<tr>'
        + '<td><span class="code-pill">' + ATW.escapeHtml(role.roleCode) + '</span></td>'
        + '<td><strong>' + ATW.escapeHtml(role.roleName) + '</strong></td>'
        + '<td class="muted-value">' + ATW.escapeHtml(ATW.shortText(role.description, 40)) + '</td>'
        + '<td><span class="count-pill">' + permissions.length + '</span></td>'
        + '<td>' + (isSystemRole(role)
          ? '<span class="role-badge role-badge-system">系统</span>'
          : '<span class="role-badge">自定义</span>') + '</td>'
        + '<td class="muted-value">' + ATW.formatDateTime(role.createTime) + '</td>'
        + '<td class="td-actions"><div class="row-actions">' + actions + '</div></td>'
        + '</tr>';
    }).join('');

    el('listSummary').textContent = '共 ' + state.roles.length + ' 个角色';
    el('pageInfo').textContent = '共 ' + state.roles.length + ' 个角色';
  }

  function renderError(message) {
    el('roleTableBody').innerHTML = '<tr><td colspan="7"><div class="table-state table-empty"><strong>加载失败</strong><span>'
      + ATW.escapeHtml(message) + '</span><button class="btn btn-ghost btn-sm" type="button" onclick="location.reload()">重新加载</button></div></td></tr>';
    el('listSummary').textContent = '加载失败';
    el('pageInfo').textContent = '加载失败';
  }

  function openCreate() {
    state.editing = null;
    el('roleForm').reset();
    el('roleId').value = '';
    el('roleCode').disabled = false;
    el('roleModalTitle').textContent = '新建角色';
    el('roleModalHint').textContent = '角色编码创建后不可修改。';
    el('roleSubmitBtn').textContent = '创建角色';
    AdminPage.openModal('roleModal');
  }

  function openEdit(role) {
    state.editing = role;
    el('roleForm').reset();
    el('roleId').value = String(role.id);
    el('roleCode').value = role.roleCode || '';
    el('roleCode').disabled = true;
    el('roleName').value = role.roleName || '';
    el('roleDescription').value = role.description || '';
    el('roleModalTitle').textContent = '编辑角色';
    el('roleModalHint').textContent = isSystemRole(role)
      ? '系统内置角色的编码不可修改，可以调整名称和描述。'
      : '角色编码创建后不可修改。';
    el('roleSubmitBtn').textContent = '保存修改';
    AdminPage.openModal('roleModal');
  }

  async function submitRole(event) {
    event.preventDefault();
    var id = el('roleId').value;
    var roleCode = el('roleCode').value.trim();
    var roleName = el('roleName').value.trim();
    var description = el('roleDescription').value.trim();

    if (!id) {
      if (!/^[A-Za-z0-9_-]{2,50}$/.test(roleCode)) {
        ATW.toast('角色编码需为 2-50 位字母、数字、下划线或短横线', 'error');
        el('roleCode').focus();
        return;
      }
    }
    if (!roleName) {
      ATW.toast('请输入角色名称', 'error');
      el('roleName').focus();
      return;
    }

    var body = { roleName: roleName, description: description };
    if (!id) {
      body.roleCode = roleCode;
    }

    var button = el('roleSubmitBtn');
    ATW.setButtonLoading(button, true, id ? '保存中...' : '创建中...');
    try {
      if (id) {
        await ATW.api('/api/roles/' + id, { method: 'PUT', body: body });
        ATW.toast('角色已更新', 'success');
      } else {
        await ATW.api('/api/roles', { method: 'POST', body: body });
        ATW.toast('角色已创建', 'success');
      }
      AdminPage.closeModal('roleModal');
      loadAll();
    } catch (e) {
      ATW.toast(e.message || '保存失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function openPermissions(role) {
    state.permissionRole = role;
    el('rolePermissionsTitle').textContent = '分配权限';
    el('rolePermissionsHint').textContent = '为“' + role.roleName + '”选择权限，保存后立即生效。';
    el('permissionSearch').value = '';
    el('permissionGroups').innerHTML = '<div class="loading-line"><span class="spinner"></span>加载权限...</div>';
    AdminPage.openModal('rolePermissionsModal');

    if (state.permissions.length === 0) {
      try {
        var permissions = await ATW.api('/api/permissions');
        state.permissions = Array.isArray(permissions) ? permissions : [];
      } catch (e) {
        el('permissionGroups').innerHTML = '<div class="empty-inline">权限列表加载失败：'
          + ATW.escapeHtml(e.message) + '</div>';
        el('permissionSelectedCount').textContent = '';
        return;
      }
    }

    var selected = {};
    var rolePermissions = role.permissions;
    if (!Array.isArray(rolePermissions)) {
      try {
        rolePermissions = await ATW.api('/api/roles/' + role.id + '/permissions');
      } catch (e) {
        rolePermissions = [];
      }
    }
    (Array.isArray(rolePermissions) ? rolePermissions : []).forEach(function (permission) {
      selected[permission.id] = true;
    });

    renderPermissionGroups(selected);
    filterPermissions();
    updateSelectedCount();
  }

  function renderPermissionGroups(selected) {
    var groups = {};
    state.permissions.forEach(function (permission) {
      var prefix = (permission.permissionCode || '').split(':')[0];
      if (!groups[prefix]) {
        groups[prefix] = [];
      }
      groups[prefix].push(permission);
    });

    var keys = Object.keys(groups).sort(function (a, b) {
      var ai = GROUP_ORDER.indexOf(a);
      var bi = GROUP_ORDER.indexOf(b);
      if (ai === -1 && bi === -1) {
        return a.localeCompare(b);
      }
      if (ai === -1) {
        return 1;
      }
      if (bi === -1) {
        return -1;
      }
      return ai - bi;
    });

    if (keys.length === 0) {
      el('permissionGroups').innerHTML = '<div class="empty-inline">暂无可分配权限。</div>';
      return;
    }

    el('permissionGroups').innerHTML = keys.map(function (prefix) {
      var items = groups[prefix];
      return '<section class="permission-group" data-group="' + ATW.escapeHtml(prefix) + '">'
        + '<div class="permission-group-head"><h3>' + ATW.escapeHtml(GROUP_LABELS[prefix] || prefix)
        + '</h3><span>' + items.length + ' 项</span></div>'
        + '<div class="permission-options">'
        + items.map(function (permission) {
          var searchText = ((permission.permissionCode || '') + ' ' + (permission.permissionName || '')).toLowerCase();
          return '<label class="check-item permission-item" data-search="' + ATW.escapeHtml(searchText) + '">'
            + '<input type="checkbox" value="' + permission.id + '"' + (selected[permission.id] ? ' checked' : '') + '>'
            + '<span class="check-item-body">'
            + '<span class="check-item-title">' + ATW.escapeHtml(permission.permissionName)
            + ' <span class="code-pill code-pill-sm">' + ATW.escapeHtml(permission.permissionCode) + '</span></span>'
            + (Number(permission.status) === 0 ? '<span class="check-item-desc text-danger">该权限当前已禁用</span>' : '')
            + '</span>'
            + '</label>';
        }).join('')
        + '</div></section>';
    }).join('');
  }

  function filterPermissions() {
    var keyword = el('permissionSearch').value.trim().toLowerCase();
    var groups = el('permissionGroups').querySelectorAll('.permission-group');
    Array.prototype.forEach.call(groups, function (group) {
      var visibleCount = 0;
      Array.prototype.forEach.call(group.querySelectorAll('.permission-item'), function (item) {
        var match = !keyword || (item.getAttribute('data-search') || '').indexOf(keyword) !== -1;
        item.hidden = !match;
        if (match) {
          visibleCount += 1;
        }
      });
      group.hidden = visibleCount === 0;
    });
  }

  function setVisiblePermissions(checked) {
    Array.prototype.forEach.call(
      el('permissionGroups').querySelectorAll('.permission-item:not([hidden]) input[type="checkbox"]'),
      function (input) {
        input.checked = checked;
      }
    );
    updateSelectedCount();
  }

  function updateSelectedCount() {
    var count = el('permissionGroups').querySelectorAll('input[type="checkbox"]:checked').length;
    el('permissionSelectedCount').textContent = '已选择 ' + count + ' 项权限';
  }

  async function saveRolePermissions() {
    if (!state.permissionRole) {
      return;
    }
    var ids = Array.prototype.map.call(
      el('permissionGroups').querySelectorAll('input[type="checkbox"]:checked'),
      function (input) {
        return Number(input.value);
      }
    );
    var button = el('saveRolePermissionsBtn');
    ATW.setButtonLoading(button, true, '保存中...');
    try {
      await ATW.api('/api/roles/' + state.permissionRole.id + '/permissions', {
        method: 'POST',
        body: ids
      });
      ATW.toast('角色权限已更新', 'success');
      AdminPage.closeModal('rolePermissionsModal');
      loadAll();
    } catch (e) {
      ATW.toast(e.message || '保存权限失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function removeRole(role) {
    var confirmed = await AdminPage.confirm({
      title: '删除角色',
      message: '确定删除角色“' + role.roleName + '”吗？拥有该角色的用户会失去对应权限。',
      confirmText: '删除角色',
      danger: true
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/roles/' + role.id, { method: 'DELETE' });
      ATW.toast('角色已删除', 'success');
      loadAll();
    } catch (e) {
      ATW.toast(e.message || '删除失败', 'error');
    }
  }
})();

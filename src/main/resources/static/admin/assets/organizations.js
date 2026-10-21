(function () {
  'use strict';

  var ATW = window.ATW;
  var AdminPage = window.AdminPage;

  var state = {
    page: 0,
    size: 10,
    keyword: '',
    items: [],
    pageData: null,
    editing: null
  };

  function el(id) {
    return document.getElementById(id);
  }

  document.addEventListener('DOMContentLoaded', function () {
    AdminPage.init({ permission: 'org:view' }).then(function (user) {
      if (!user || !AdminPage.can('org:view')) {
        return;
      }
      init();
    });
  });

  function init() {
    AdminPage.bindModalClose();
    bindEvents();
    loadList();
  }

  function bindEvents() {
    el('filterForm').addEventListener('submit', function (event) {
      event.preventDefault();
      state.keyword = el('keyword').value.trim();
      state.page = 0;
      loadList();
    });
    el('resetFilterBtn').addEventListener('click', function () {
      el('keyword').value = '';
      state.keyword = '';
      state.page = 0;
      loadList();
    });
    el('refreshBtn').addEventListener('click', loadList);
    el('createBtn').addEventListener('click', openCreate);
    el('orgForm').addEventListener('submit', submitForm);
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
    el('orgTableBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-action]');
      if (!button) {
        return;
      }
      var id = Number(button.dataset.id);
      var item = state.items.filter(function (org) {
        return org.id === id;
      })[0];
      if (!item) {
        return;
      }
      if (button.dataset.action === 'edit') {
        openEdit(item);
      } else if (button.dataset.action === 'toggle') {
        toggleStatus(item);
      } else if (button.dataset.action === 'delete') {
        removeOrganization(item);
      }
    });
  }

  async function loadList() {
    el('listSummary').textContent = '加载中...';
    var query = new URLSearchParams();
    query.set('page', String(state.page));
    query.set('size', String(state.size));
    if (state.keyword) {
      query.set('keyword', state.keyword);
    }
    try {
      var result = await ATW.api('/api/organizations?' + query.toString());
      state.pageData = result || { items: [], totalElements: 0, totalPages: 0 };
      state.items = Array.isArray(result.items) ? result.items : [];
      if (state.items.length === 0 && state.page > 0 && state.pageData.totalElements > 0) {
        state.page -= 1;
        return loadList();
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

  function renderTable() {
    var body = el('orgTableBody');
    if (state.items.length === 0) {
      body.innerHTML = '<tr><td colspan="6"><div class="table-state table-empty"><strong>暂无组织</strong><span>可以点击右上角“新建组织”创建第一个组织。</span></div></td></tr>';
      el('listSummary').textContent = '共 0 个组织';
      return;
    }

    var canUpdate = AdminPage.can('org:update');
    var canDelete = AdminPage.can('org:delete');
    body.innerHTML = state.items.map(function (org) {
      var actions = '';
      if (canUpdate) {
        actions += '<button class="mini-btn" type="button" data-action="edit" data-id="' + org.id + '">编辑</button>';
        actions += '<button class="mini-btn" type="button" data-action="toggle" data-id="' + org.id + '">'
          + (Number(org.status) === 1 ? '禁用' : '启用') + '</button>';
      }
      if (canDelete) {
        actions += '<button class="mini-btn is-danger" type="button" data-action="delete" data-id="' + org.id + '">删除</button>';
      }
      if (!actions) {
        actions = '<span class="muted-value">只读</span>';
      }
      return '<tr>'
        + '<td><span class="code-pill">' + ATW.escapeHtml(org.orgCode) + '</span></td>'
        + '<td><strong>' + ATW.escapeHtml(org.orgName) + '</strong></td>'
        + '<td class="muted-value">' + ATW.escapeHtml(ATW.shortText(org.description, 42)) + '</td>'
        + '<td>' + statusBadge(org.status) + '</td>'
        + '<td class="muted-value">' + ATW.formatDateTime(org.createTime) + '</td>'
        + '<td class="td-actions"><div class="row-actions">' + actions + '</div></td>'
        + '</tr>';
    }).join('');

    el('listSummary').textContent = '共 ' + state.pageData.totalElements + ' 个组织'
      + (state.keyword ? '，关键字：' + state.keyword : '');
  }

  function statusBadge(status) {
    var enabled = Number(status) === 1;
    return '<span class="status-badge ' + (enabled ? 'status-activated' : 'status-disabled') + '">'
      + (enabled ? '启用' : '禁用') + '</span>';
  }

  function renderError(message) {
    el('orgTableBody').innerHTML = '<tr><td colspan="6"><div class="table-state table-empty"><strong>加载失败</strong><span>'
      + ATW.escapeHtml(message) + '</span><button class="btn btn-ghost btn-sm" type="button" onclick="location.reload()">重新加载</button></div></td></tr>';
    el('listSummary').textContent = '加载失败';
  }

  function renderPager() {
    var data = state.pageData || { totalElements: 0, totalPages: 0 };
    var totalPages = data.totalPages || 0;
    el('pageInfo').textContent = '共 ' + data.totalElements + ' 个组织';
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
    el('orgForm').reset();
    el('orgId').value = '';
    el('orgModalTitle').textContent = '新建组织';
    el('orgModalHint').textContent = '创建组织时会自动生成管理员角色和普通管理员角色。';
    el('orgStatusField').hidden = true;
    el('orgAdminFields').hidden = false;
    el('orgSubmitBtn').textContent = '创建组织';
    AdminPage.openModal('orgModal');
  }

  function openEdit(org) {
    state.editing = org;
    el('orgForm').reset();
    el('orgId').value = String(org.id);
    el('orgCode').value = org.orgCode || '';
    el('orgName').value = org.orgName || '';
    el('orgDescription').value = org.description || '';
    el('orgStatus').value = String(org.status);
    el('orgModalTitle').textContent = '编辑组织';
    el('orgModalHint').textContent = '组织编码和名称必须保持唯一。';
    el('orgStatusField').hidden = false;
    el('orgAdminFields').hidden = true;
    el('orgSubmitBtn').textContent = '保存修改';
    AdminPage.openModal('orgModal');
  }

  async function submitForm(event) {
    event.preventDefault();
    var id = el('orgId').value;
    var body = {
      orgCode: el('orgCode').value.trim(),
      orgName: el('orgName').value.trim(),
      description: el('orgDescription').value.trim()
    };
    if (!body.orgCode) {
      ATW.toast('请输入组织编码', 'error');
      el('orgCode').focus();
      return;
    }
    if (!body.orgName) {
      ATW.toast('请输入组织名称', 'error');
      el('orgName').focus();
      return;
    }

    if (!id) {
      body.adminUsername = el('adminUsername').value.trim();
      body.adminPassword = el('adminPassword').value;
      body.adminRealName = el('adminRealName').value.trim();
      if (!body.adminUsername || !body.adminRealName || !body.adminPassword) {
        ATW.toast('请填写完整的初始管理员信息', 'error');
        return;
      }
      if (body.adminPassword.length < 6) {
        ATW.toast('管理员密码至少 6 个字符', 'error');
        el('adminPassword').focus();
        return;
      }
    } else {
      body.status = Number(el('orgStatus').value);
    }

    var button = el('orgSubmitBtn');
    ATW.setButtonLoading(button, true, id ? '保存中...' : '创建中...');
    try {
      if (id) {
        await ATW.api('/api/organizations/' + id, {
          method: 'PUT',
          body: body
        });
        ATW.toast('组织已更新', 'success');
      } else {
        await ATW.api('/api/organizations', {
          method: 'POST',
          body: body
        });
        ATW.toast('组织已创建，初始管理员可以登录了', 'success');
      }
      AdminPage.closeModal('orgModal');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '保存失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function toggleStatus(org) {
    var next = Number(org.status) === 1 ? 0 : 1;
    var confirmed = await AdminPage.confirm({
      title: next === 0 ? '禁用组织' : '启用组织',
      message: next === 0
        ? '禁用后该组织下的账号将无法登录，确定继续吗？'
        : '启用后该组织下的账号可以重新登录，确定继续吗？',
      confirmText: next === 0 ? '禁用' : '启用',
      danger: next === 0
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/organizations/' + org.id, {
        method: 'PUT',
        body: { status: next }
      });
      ATW.toast(next === 1 ? '组织已启用' : '组织已禁用', 'success');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '操作失败', 'error');
    }
  }

  async function removeOrganization(org) {
    var confirmed = await AdminPage.confirm({
      title: '删除组织',
      message: '删除“' + org.orgName + '”会同时删除该组织下的所有用户和角色，且无法恢复。确定继续吗？',
      confirmText: '删除组织',
      danger: true
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/organizations/' + org.id, { method: 'DELETE' });
      ATW.toast('组织已删除', 'success');
      if (state.items.length === 1 && state.page > 0) {
        state.page -= 1;
      }
      loadList();
    } catch (e) {
      ATW.toast(e.message || '删除失败', 'error');
    }
  }
})();

(function () {
  'use strict';

  var ATW = window.ATW;

  var state = {
    page: 0,
    size: 10,
    status: '',
    batch: '',
    keyword: '',
    items: [],
    pageData: null,
    canView: false,
    canCreate: false,
    user: null,
    detailItem: null,
    result: null,
    listSeq: 0,
    statsSeq: 0,
    lastFocused: null
  };

  function el(id) {
    return document.getElementById(id);
  }

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    if (!ATW.getToken()) {
      ATW.redirectToLogin();
      return;
    }

    bindEvents();

    var user = ATW.getUser();
    if (user) {
      applyUser(user);
    }

    try {
      var fresh = await ATW.api('/api/auth/userinfo', { timeout: 10000 });
      if (fresh) {
        user = fresh;
        ATW.saveUser(user);
        applyUser(user);
      }
    } catch (e) {
      if (e.status === 401) {
        return;
      }
      ATW.toast(e.message || '无法获取账号信息', 'error');
    }

    if (!user) {
      ATW.clearAuth();
      ATW.redirectToLogin('expired');
      return;
    }

    setupPermissions(user);
  }

  function bindEvents() {
    el('filterForm').addEventListener('submit', function (event) {
      event.preventDefault();
      state.keyword = el('keyword').value.trim();
      state.status = el('status').value;
      state.batch = el('batch').value.trim();
      state.page = 0;
      updateActiveStat();
      loadList();
    });

    el('resetFilterBtn').addEventListener('click', resetFilters);
    el('refreshBtn').addEventListener('click', refreshAll);
    el('openGenerateBtn').addEventListener('click', openGenerate);
    el('noViewGenerateBtn').addEventListener('click', openGenerate);
    el('logoutBtn').addEventListener('click', logout);
    el('logoutSideBtn').addEventListener('click', logout);
    el('noPermissionLogout').addEventListener('click', logout);
    el('menuBtn').addEventListener('click', toggleSidebar);
    el('sidebarScrim').addEventListener('click', closeSidebar);

    el('statsGrid').addEventListener('click', function (event) {
      var card = event.target.closest('[data-status]');
      if (!card) {
        return;
      }
      state.status = card.dataset.status || '';
      state.keyword = '';
      state.batch = '';
      state.page = 0;
      el('keyword').value = '';
      el('batch').value = '';
      el('status').value = state.status;
      updateActiveStat();
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
      var totalPages = state.pageData ? state.pageData.totalPages : 0;
      goPage(Math.max(0, totalPages - 1));
    });

    el('pageSize').addEventListener('change', function () {
      state.size = Number(el('pageSize').value) || 10;
      state.page = 0;
      loadList();
    });

    el('generateForm').addEventListener('submit', submitGenerate);
    el('copyCodesBtn').addEventListener('click', copyGeneratedCodes);
    el('downloadCodesBtn').addEventListener('click', downloadGeneratedCodes);
    el('detailToggleBtn').addEventListener('click', function () {
      if (state.detailItem) {
        toggleStatus(state.detailItem.id, el('detailToggleBtn'));
      }
    });

    el('detailBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-copy-field]');
      if (!button || !state.detailItem) {
        return;
      }
      var field = button.dataset.copyField;
      var value = '';
      if (field === 'code') {
        value = state.detailItem.code;
      } else if (field === 'license') {
        value = state.detailItem.licenseKey;
      } else if (field === 'machine') {
        value = state.detailItem.machineCode;
      } else if (field === 'licenseId') {
        value = state.detailItem.licenseId;
      }
      copyValue(value, '已复制');
    });

    el('codeTableBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-action]');
      if (!button) {
        return;
      }
      var action = button.dataset.action;
      var id = Number(button.dataset.id);
      if (action === 'detail') {
        showDetail(id);
      } else if (action === 'toggle') {
        toggleStatus(id, button);
      } else if (action === 'copy-code') {
        var item = findItem(id);
        if (item) {
          copyValue(item.code, '激活码已复制');
        }
      } else if (action === 'generate') {
        openGenerate();
      } else if (action === 'clear-filters') {
        resetFilters();
      } else if (action === 'retry') {
        loadList();
      }
    });

    document.querySelectorAll('[data-close]').forEach(function (button) {
      button.addEventListener('click', function () {
        closeModal(button.dataset.close);
      });
    });

    document.querySelectorAll('.modal').forEach(function (modal) {
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
      var openModals = document.querySelectorAll('.modal.is-open');
      if (openModals.length > 0) {
        closeModal(openModals[openModals.length - 1].id);
      }
    });

    window.addEventListener('resize', function () {
      if (window.innerWidth > 900) {
        closeSidebar();
      }
    });
  }

  function applyUser(user) {
    state.user = user;
    var name = user.realName || user.username || '-';
    var org = user.orgName || (Array.isArray(user.roles) ? user.roles.join(' / ') : '') || '-';
    var avatar = ATW.initials(user);

    el('userAvatar').textContent = avatar;
    el('topbarAvatar').textContent = avatar;
    el('sidebarUserName').textContent = name;
    el('sidebarUserOrg').textContent = org;
    el('topbarUserName').textContent = name;
    el('topbarUserOrg').textContent = org;
  }

  function setupPermissions(user) {
    var permissions = Array.isArray(user.permissions) ? user.permissions : [];
    state.canView = permissions.indexOf('license:view-code') !== -1;
    state.canCreate = permissions.indexOf('license:create-code') !== -1;

    if (!state.canView && !state.canCreate) {
      el('noPermission').hidden = false;
      el('noViewNotice').hidden = true;
      el('pageBody').hidden = true;
      return;
    }

    if (!state.canView) {
      el('noPermission').hidden = true;
      el('noViewNotice').hidden = false;
      el('pageBody').hidden = true;
      return;
    }

    el('noPermission').hidden = true;
    el('noViewNotice').hidden = true;
    el('pageBody').hidden = false;
    el('openGenerateBtn').hidden = !state.canCreate;

    updateActiveStat();
    loadStats();
    loadList();
  }

  function resetFilters() {
    state.keyword = '';
    state.status = '';
    state.batch = '';
    state.page = 0;
    el('keyword').value = '';
    el('status').value = '';
    el('batch').value = '';
    updateActiveStat();
    loadList();
  }

  function updateActiveStat() {
    document.querySelectorAll('.stat-card').forEach(function (card) {
      card.classList.toggle('is-active', (card.dataset.status || '') === state.status);
    });
  }

  async function refreshAll() {
    var button = el('refreshBtn');
    ATW.setButtonLoading(button, true, '刷新中...');
    try {
      await Promise.all([loadStats(), loadList()]);
      ATW.toast('数据已刷新', 'success');
    } catch (e) {
      ATW.toast(e.message || '刷新失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function loadStats() {
    if (!state.canView) {
      return;
    }
    var seq = ++state.statsSeq;
    ['statAll', 'statUnused', 'statActivated', 'statDisabled'].forEach(function (id) {
      el(id).textContent = '\u2014';
    });

    function count(status) {
      var query = '?page=0&size=1';
      if (status !== null) {
        query += '&status=' + status;
      }
      return ATW.api('/api/license/codes' + query).then(function (data) {
        return data.totalElements;
      }).catch(function () {
        return null;
      });
    }

    var results = await Promise.all([count(null), count(0), count(1), count(2)]);
    if (seq !== state.statsSeq) {
      return;
    }

    el('statAll').textContent = formatCount(results[0]);
    el('statUnused').textContent = formatCount(results[1]);
    el('statActivated').textContent = formatCount(results[2]);
    el('statDisabled').textContent = formatCount(results[3]);
  }

  function formatCount(value) {
    if (value === null || value === undefined) {
      return '\u2014';
    }
    return Number(value).toLocaleString('zh-CN');
  }

  async function loadList() {
    if (!state.canView) {
      return;
    }
    var seq = ++state.listSeq;
    setTableLoading();

    var query = new URLSearchParams();
    query.set('page', String(state.page));
    query.set('size', String(state.size));
    if (state.status !== '') {
      query.set('status', state.status);
    }
    if (state.batch) {
      query.set('batch', state.batch);
    }
    if (state.keyword) {
      query.set('keyword', state.keyword);
    }

    try {
      var data = await ATW.api('/api/license/codes?' + query.toString());
      if (seq !== state.listSeq) {
        return;
      }
      if (data.empty && state.page > 0) {
        state.page = Math.max(0, data.totalPages - 1);
        loadList();
        return;
      }
      state.pageData = data;
      state.page = data.pageNumber;
      state.size = data.pageSize;
      state.items = data.items || [];
      renderTable();
      renderPagination();
      renderSummary();
    } catch (e) {
      if (seq !== state.listSeq) {
        return;
      }
      showTableState(e.message || '加载失败', '重试', 'retry');
      el('listSummary').textContent = '加载失败';
    }
  }

  function renderSummary() {
    var total = state.pageData ? state.pageData.totalElements : 0;
    var filtered = state.keyword || state.batch || state.status !== '';
    el('listSummary').textContent = filtered
      ? '筛选结果 ' + Number(total).toLocaleString('zh-CN') + ' 条'
      : '共 ' + Number(total).toLocaleString('zh-CN') + ' 条激活码';
  }

  function setTableLoading() {
    showTableState('正在加载激活码...', null, null);
  }

  function showTableState(message, actionLabel, action) {
    var tbody = el('codeTableBody');
    while (tbody.firstChild) {
      tbody.removeChild(tbody.firstChild);
    }
    var row = document.createElement('tr');
    var cell = document.createElement('td');
    cell.colSpan = 9;
    cell.className = 'table-state';
    cell.textContent = message;
    if (actionLabel && action) {
      var button = document.createElement('button');
      button.type = 'button';
      button.className = 'btn btn-ghost btn-sm';
      button.dataset.action = action;
      button.textContent = actionLabel;
      cell.appendChild(button);
    }
    row.appendChild(cell);
    tbody.appendChild(row);
  }

  function renderTable() {
    var tbody = el('codeTableBody');
    while (tbody.firstChild) {
      tbody.removeChild(tbody.firstChild);
    }

    if (!state.items.length) {
      var filtered = state.keyword || state.batch || state.status !== '';
      var row = document.createElement('tr');
      var cell = document.createElement('td');
      cell.colSpan = 9;
      cell.className = 'table-state table-empty';
      var title = document.createElement('strong');
      title.textContent = filtered ? '没有符合条件的激活码' : '还没有激活码';
      var hint = document.createElement('span');
      hint.textContent = filtered ? '试试调整关键字、状态或批次。' : '生成第一批激活码后，会显示在这里。';
      cell.appendChild(title);
      cell.appendChild(hint);
      if (filtered) {
        var clear = document.createElement('button');
        clear.type = 'button';
        clear.className = 'btn btn-ghost btn-sm';
        clear.dataset.action = 'clear-filters';
        clear.textContent = '清除筛选';
        cell.appendChild(clear);
      } else if (state.canCreate) {
        var create = document.createElement('button');
        create.type = 'button';
        create.className = 'btn btn-primary btn-sm';
        create.dataset.action = 'generate';
        create.textContent = '生成第一批激活码';
        cell.appendChild(create);
      }
      row.appendChild(cell);
      tbody.appendChild(row);
      return;
    }

    state.items.forEach(function (item) {
      var row = document.createElement('tr');

      var codeCell = document.createElement('td');
      var codeWrap = document.createElement('div');
      codeWrap.className = 'code-cell';
      var code = document.createElement('code');
      code.textContent = item.code;
      var copy = document.createElement('button');
      copy.type = 'button';
      copy.className = 'mini-btn';
      copy.dataset.action = 'copy-code';
      copy.dataset.id = item.id;
      copy.textContent = '复制';
      codeWrap.appendChild(code);
      codeWrap.appendChild(copy);
      codeCell.appendChild(codeWrap);
      row.appendChild(codeCell);

      var statusCell = document.createElement('td');
      var meta = ATW.statusMeta(item.status);
      var badge = document.createElement('span');
      badge.className = 'status-badge ' + meta.className;
      badge.textContent = meta.label;
      statusCell.appendChild(badge);
      row.appendChild(statusCell);

      row.appendChild(textCell(item.batch || '\u2014'));
      row.appendChild(textCell(item.licensee || '\u2014'));
      row.appendChild(textCell(item.edition || '\u2014'));
      row.appendChild(textCell(ATW.formatDays(item.validDays)));

      var machineCell = document.createElement('td');
      var machine = document.createElement('span');
      machine.className = 'mono muted-value';
      machine.textContent = item.machineCode ? ATW.shortText(item.machineCode, 20) : '\u2014';
      machine.title = item.machineCode || '';
      machineCell.appendChild(machine);
      row.appendChild(machineCell);

      row.appendChild(textCell(ATW.formatDateTime(item.createTime)));

      var actionCell = document.createElement('td');
      actionCell.className = 'td-actions';
      var actions = document.createElement('div');
      actions.className = 'row-actions';

      var detailButton = document.createElement('button');
      detailButton.type = 'button';
      detailButton.className = 'mini-btn';
      detailButton.dataset.action = 'detail';
      detailButton.dataset.id = item.id;
      detailButton.textContent = '详情';
      actions.appendChild(detailButton);

      if (state.canCreate) {
        var toggleButton = document.createElement('button');
        toggleButton.type = 'button';
        toggleButton.className = 'mini-btn' + (Number(item.status) === 2 ? '' : ' is-danger');
        toggleButton.dataset.action = 'toggle';
        toggleButton.dataset.id = item.id;
        toggleButton.textContent = Number(item.status) === 2 ? '启用' : '禁用';
        if (Number(item.status) === 1) {
          toggleButton.title = '禁用后已签发的 License 将立即失效';
        }
        actions.appendChild(toggleButton);
      }

      actionCell.appendChild(actions);
      row.appendChild(actionCell);
      tbody.appendChild(row);
    });
  }

  function textCell(text) {
    var cell = document.createElement('td');
    cell.textContent = text;
    return cell;
  }

  function renderPagination() {
    var data = state.pageData;
    var totalPages = data ? data.totalPages : 0;
    var total = data ? data.totalElements : 0;
    var current = totalPages > 0 ? state.page + 1 : 0;

    el('pageNumber').textContent = current + ' / ' + totalPages;
    el('pageInfo').textContent = total > 0
      ? '共 ' + Number(total).toLocaleString('zh-CN') + ' 条，当前第 ' + (state.page + 1) + ' 页'
      : '共 0 条';
    el('pageSize').value = String(state.size);

    var noPrev = totalPages === 0 || state.page <= 0;
    var noNext = totalPages === 0 || state.page >= totalPages - 1;
    el('firstPageBtn').disabled = noPrev;
    el('prevPageBtn').disabled = noPrev;
    el('nextPageBtn').disabled = noNext;
    el('lastPageBtn').disabled = noNext;
  }

  function goPage(page) {
    if (!state.pageData) {
      return;
    }
    var totalPages = state.pageData.totalPages;
    if (page < 0 || page >= totalPages || page === state.page) {
      return;
    }
    state.page = page;
    loadList();
  }

  function findItem(id) {
    for (var i = 0; i < state.items.length; i++) {
      if (Number(state.items[i].id) === Number(id)) {
        return state.items[i];
      }
    }
    return null;
  }

  function openGenerate() {
    if (!state.canCreate) {
      ATW.toast('当前账号没有生成激活码权限', 'error');
      return;
    }
    el('generateForm').reset();
    el('genCount').value = '1';
    el('genEdition').value = 'standard';
    openModal('generateModal');
    setTimeout(function () {
      el('genCount').focus();
      el('genCount').select();
    }, 80);
  }

  async function submitGenerate(event) {
    event.preventDefault();
    var button = el('generateBtn');

    var count = Number(el('genCount').value);
    if (!Number.isInteger(count) || count < 1 || count > 500) {
      ATW.toast('生成数量必须是 1 到 500 的整数', 'error');
      el('genCount').focus();
      return;
    }

    var validDays;
    var deadlineDays;
    try {
      validDays = optionalNonNegativeInt(el('genValidDays'), 'License 有效天数');
      deadlineDays = optionalNonNegativeInt(el('genDeadlineDays'), '激活码截止天数');
    } catch (e) {
      ATW.toast(e.message, 'error');
      return;
    }

    var payload = {
      count: count,
      batch: trimOrNull(el('genBatch').value),
      licensee: trimOrNull(el('genLicensee').value),
      edition: trimOrNull(el('genEdition').value),
      validDays: validDays,
      deadlineDays: deadlineDays,
      remark: trimOrNull(el('genRemark').value)
    };

    ATW.setButtonLoading(button, true, '生成中...');
    try {
      var data = await ATW.api('/api/license/codes', {
        method: 'POST',
        body: payload,
        timeout: 30000
      });
      closeModal('generateModal');
      showResult(data);
      state.keyword = '';
      state.status = '';
      state.batch = data.batch || '';
      state.page = 0;
      el('keyword').value = '';
      el('status').value = '';
      el('batch').value = state.batch;
      updateActiveStat();
      await Promise.all([loadStats(), loadList()]);
      ATW.toast('已生成 ' + data.count + ' 个激活码', 'success');
    } catch (e) {
      ATW.toast(e.message || '生成失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  function optionalNonNegativeInt(input, label) {
    var raw = input.value.trim();
    if (!raw) {
      return null;
    }
    var value = Number(raw);
    if (!Number.isInteger(value) || value < 0) {
      input.focus();
      throw new Error(label + '必须是非负整数');
    }
    return value;
  }

  function trimOrNull(value) {
    var text = String(value || '').trim();
    return text ? text : null;
  }

  function showResult(data) {
    state.result = data;
    var batch = data.batch ? '，批次 ' + data.batch : '';
    el('resultSummary').textContent = '已生成 ' + data.count + ' 个激活码' + batch;
    el('generatedCodes').value = (data.codes || []).join('\n');
    openModal('resultModal');
  }

  async function copyGeneratedCodes() {
    var codes = el('generatedCodes').value;
    var copied = await ATW.copyText(codes);
    ATW.toast(copied ? '已复制全部激活码' : '复制失败，请手动选择文本', copied ? 'success' : 'error');
  }

  function downloadGeneratedCodes() {
    var codes = el('generatedCodes').value;
    var batch = state.result && state.result.batch ? state.result.batch : 'codes';
    var safeBatch = batch.replace(/[^\w.-]+/g, '_');
    var now = new Date();
    var stamp = now.getFullYear()
      + pad(now.getMonth() + 1)
      + pad(now.getDate())
      + '-'
      + pad(now.getHours())
      + pad(now.getMinutes());
    var blob = new Blob(['\ufeff' + codes], { type: 'text/plain;charset=utf-8' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = 'activation-codes-' + safeBatch + '-' + stamp + '.txt';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(function () {
      URL.revokeObjectURL(url);
    }, 1000);
  }

  function pad(value) {
    return value < 10 ? '0' + value : String(value);
  }

  function showDetail(id) {
    var item = findItem(id);
    if (!item) {
      return;
    }
    state.detailItem = item;

    var body = el('detailBody');
    while (body.firstChild) {
      body.removeChild(body.firstChild);
    }

    addDetailItem(body, '激活码', copyableValue(item.code, 'code'), true);
    addDetailItem(body, '状态', statusBadge(item.status), false);
    addDetailItem(body, '批次', textValue(item.batch || '\u2014'), false);
    addDetailItem(body, '被授权方', textValue(item.licensee || '\u2014'), false);
    addDetailItem(body, '版本', textValue(item.edition || '\u2014'), false);
    addDetailItem(body, 'License 有效天数', textValue(ATW.formatDays(item.validDays)), false);
    addDetailItem(body, '激活码截止', textValue(item.deadlineAt ? ATW.formatDateTime(item.deadlineAt) : '不限'), false);
    addDetailItem(body, '绑定机器', item.machineCode ? copyableValue(item.machineCode, 'machine', true) : textValue('尚未绑定'), false);
    addDetailItem(body, 'License ID', item.licenseId ? copyableValue(item.licenseId, 'licenseId', true) : textValue('尚未签发'), false);
    addDetailItem(body, '激活时间', textValue(ATW.formatDateTime(item.activatedAt)), false);
    addDetailItem(body, 'License 到期', textValue(ATW.licenseExpiry(item)), false);
    addDetailItem(body, '生成时间', textValue(ATW.formatDateTime(item.createTime)), false);
    addDetailItem(body, '更新时间', textValue(ATW.formatDateTime(item.updateTime)), false);
    addDetailItem(body, '备注', textValue(item.remark || '\u2014'), true);

    var licenseNode;
    if (item.licenseKey) {
      licenseNode = document.createElement('div');
      licenseNode.className = 'license-block';
      var area = document.createElement('textarea');
      area.className = 'license-output';
      area.readOnly = true;
      area.spellcheck = false;
      area.value = item.licenseKey;
      var copyButton = document.createElement('button');
      copyButton.type = 'button';
      copyButton.className = 'btn btn-ghost btn-sm';
      copyButton.dataset.copyField = 'license';
      copyButton.textContent = '复制 License';
      licenseNode.appendChild(area);
      licenseNode.appendChild(copyButton);
    } else {
      licenseNode = textValue('尚未激活，激活后才会签发 License。');
    }
    addDetailItem(body, 'License 原文', licenseNode, true);

    var toggle = el('detailToggleBtn');
    toggle.hidden = !state.canCreate;
    if (state.canCreate) {
      toggle.textContent = Number(item.status) === 2 ? '启用激活码' : '禁用激活码';
      toggle.className = 'btn ' + (Number(item.status) === 2 ? 'btn-ghost' : 'btn-danger-ghost');
    }

    openModal('detailModal');
  }

  function addDetailItem(container, label, valueNode, full) {
    var item = document.createElement('div');
    item.className = 'detail-item' + (full ? ' detail-item-full' : '');
    var labelNode = document.createElement('span');
    labelNode.className = 'detail-label';
    labelNode.textContent = label;
    var value = document.createElement('div');
    value.className = 'detail-value';
    value.appendChild(valueNode);
    item.appendChild(labelNode);
    item.appendChild(value);
    container.appendChild(item);
  }

  function textValue(text) {
    var span = document.createElement('span');
    span.textContent = text;
    return span;
  }

  function statusBadge(status) {
    var meta = ATW.statusMeta(status);
    var badge = document.createElement('span');
    badge.className = 'status-badge ' + meta.className;
    badge.textContent = meta.label;
    return badge;
  }

  function copyableValue(value, field, mono) {
    var wrap = document.createElement('span');
    wrap.className = 'value-with-action';
    var text = document.createElement('span');
    text.className = mono ? 'mono break-all' : 'mono';
    text.textContent = value;
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'mini-btn';
    button.dataset.copyField = field;
    button.textContent = '复制';
    wrap.appendChild(text);
    wrap.appendChild(button);
    return wrap;
  }

  async function toggleStatus(id, button) {
    var item = findItem(id);
    if (!item) {
      return;
    }

    var disabling = Number(item.status) !== 2;
    var target = disabling ? 2 : (item.licenseId ? 1 : 0);
    var message;
    if (disabling) {
      message = Number(item.status) === 1
        ? '禁用后该激活码对应的 License 将立即失效，确认继续？'
        : '确认禁用该激活码？';
    } else {
      message = '确认启用该激活码？';
    }
    if (!window.confirm(message)) {
      return;
    }

    ATW.setButtonLoading(button, true, '处理中...');
    try {
      await ATW.api('/api/license/codes/' + item.id + '/status?status=' + target, {
        method: 'PUT'
      });
      if (state.detailItem && Number(state.detailItem.id) === Number(item.id)) {
        closeModal('detailModal');
        state.detailItem = null;
      }
      ATW.toast('状态已更新', 'success');
      await Promise.all([loadStats(), loadList()]);
    } catch (e) {
      ATW.setButtonLoading(button, false);
      ATW.toast(e.message || '状态更新失败', 'error');
    }
  }

  async function copyValue(value, successMessage) {
    if (!value) {
      return;
    }
    var copied = await ATW.copyText(value);
    ATW.toast(copied ? successMessage : '复制失败，请手动选择文本', copied ? 'success' : 'error');
  }

  function openModal(id) {
    var modal = el(id);
    if (!modal) {
      return;
    }
    document.querySelectorAll('.modal.is-open').forEach(function (open) {
      closeModal(open.id);
    });
    state.lastFocused = document.activeElement;
    modal.hidden = false;
    document.body.classList.add('modal-open');
    void modal.offsetWidth;
    modal.classList.add('is-open');
    setTimeout(function () {
      var focusable = modal.querySelector('input:not([type="hidden"]), select, textarea, button:not([data-close])');
      if (focusable) {
        focusable.focus();
      }
    }, 60);
  }

  function closeModal(id) {
    var modal = el(id);
    if (!modal || !modal.classList.contains('is-open')) {
      if (modal) {
        modal.hidden = true;
      }
      return;
    }
    modal.classList.remove('is-open');
    setTimeout(function () {
      if (!modal.classList.contains('is-open')) {
        modal.hidden = true;
      }
    }, 170);
    if (!document.querySelector('.modal.is-open')) {
      document.body.classList.remove('modal-open');
    }
    if (state.lastFocused && document.contains(state.lastFocused)) {
      try {
        state.lastFocused.focus();
      } catch (e) {
      }
    }
  }

  function toggleSidebar() {
    var sidebar = el('sidebar');
    var scrim = el('sidebarScrim');
    var open = !sidebar.classList.contains('is-open');
    sidebar.classList.toggle('is-open', open);
    document.body.classList.toggle('nav-open', open);
    scrim.hidden = !open;
  }

  function closeSidebar() {
    el('sidebar').classList.remove('is-open');
    document.body.classList.remove('nav-open');
    el('sidebarScrim').hidden = true;
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
})();

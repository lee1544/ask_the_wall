(function () {
  'use strict';

  var ATW = window.ATW;
  var AdminPage = window.AdminPage;

  var state = {
    items: []
  };

  function el(id) {
    return document.getElementById(id);
  }

  document.addEventListener('DOMContentLoaded', function () {
    AdminPage.init({ permission: 'model:view' }).then(function (user) {
      if (!user || !AdminPage.can('model:view')) {
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
    el('refreshBtn').addEventListener('click', loadList);
    el('createBtn').addEventListener('click', openCreate);
    el('modelForm').addEventListener('submit', submitForm);
    el('modelTableBody').addEventListener('click', function (event) {
      var button = event.target.closest('[data-action]');
      if (!button) {
        return;
      }
      var item = state.items.filter(function (config) {
        return String(config.id) === button.dataset.id;
      })[0];
      if (!item) {
        return;
      }
      if (button.dataset.action === 'edit') {
        openEdit(item);
      } else if (button.dataset.action === 'test') {
        testModel(item, button);
      } else if (button.dataset.action === 'activate') {
        activateModel(item);
      } else if (button.dataset.action === 'delete') {
        removeModel(item);
      }
    });
  }

  async function loadList() {
    el('modelSummary').textContent = '加载中...';
    try {
      var result = await ATW.api('/api/model-configs');
      state.items = Array.isArray(result) ? result : [];
      renderTable();
    } catch (e) {
      state.items = [];
      renderError(e.message);
    }
  }

  function renderTable() {
    var body = el('modelTableBody');
    if (state.items.length === 0) {
      body.innerHTML = '<tr><td colspan="8"><div class="table-state table-empty">'
        + '<strong>还没有模型配置</strong>'
        + '<span>当前使用 application.properties 中的环境默认模型。新增配置并启用后会切换到数据库配置。</span>'
        + '</div></td></tr>';
      el('modelSummary').textContent = '共 0 个配置';
      return;
    }

    var canUpdate = AdminPage.can('model:update');
    body.innerHTML = state.items.map(function (item) {
      var actions = '';
      if (canUpdate) {
        actions += '<button class="mini-btn" type="button" data-action="edit" data-id="' + item.id + '">编辑</button>';
        actions += '<button class="mini-btn" type="button" data-action="test" data-id="' + item.id + '">测试</button>';
        if (!item.active) {
          actions += '<button class="mini-btn" type="button" data-action="activate" data-id="' + item.id + '">启用</button>';
          actions += '<button class="mini-btn is-danger" type="button" data-action="delete" data-id="' + item.id + '">删除</button>';
        }
      }
      if (!actions) {
        actions = '<span class="muted-value">只读</span>';
      }

      return '<tr>'
        + '<td><div class="cell-stack"><strong>' + ATW.escapeHtml(item.configName) + '</strong>'
        + activeBadge(item.active) + '</div></td>'
        + '<td><span class="code-pill">' + ATW.escapeHtml(item.modelName) + '</span></td>'
        + '<td class="muted-value" title="' + ATW.escapeHtml(item.baseUrl) + '">'
        + ATW.escapeHtml(ATW.shortText(item.baseUrl, 44)) + '</td>'
        + '<td><div class="cell-stack"><span class="code-pill">' + ATW.escapeHtml(item.apiKeyEnv) + '</span>'
        + apiKeyBadge(item.apiKeyConfigured) + '</div></td>'
        + '<td class="muted-value">' + Number(item.contextBudget || 0).toLocaleString()
        + ' / ' + Number(item.maxOutputTokens || 0).toLocaleString() + '</td>'
        + '<td>' + webSearchLabel(item) + '</td>'
        + '<td class="muted-value">' + ATW.formatDateTime(item.updateTime) + '</td>'
        + '<td class="td-actions"><div class="row-actions">' + actions + '</div></td>'
        + '</tr>';
    }).join('');

    var active = state.items.filter(function (item) {
      return item.active;
    })[0];
    el('modelSummary').textContent = '共 ' + state.items.length + ' 个配置'
      + (active ? '，当前启用：' + active.configName : '，当前使用环境默认配置');
  }

  function activeBadge(active) {
    return '<span class="status-badge ' + (active ? 'status-activated' : 'status-disabled') + '">'
      + (active ? '启用中' : '未启用') + '</span>';
  }

  function apiKeyBadge(configured) {
    return '<span class="status-badge ' + (configured ? 'status-activated' : 'status-disabled') + '">'
      + (configured ? '已配置' : '未配置') + '</span>';
  }

  function webSearchLabel(item) {
    if (!item.webSearchEnabled) {
      return '关闭';
    }
    return item.webSearchMode === 'QWEN_ENABLE_SEARCH' ? '千问内置' : 'DeepSeek 工具';
  }

  function renderError(message) {
    el('modelTableBody').innerHTML = '<tr><td colspan="8"><div class="table-state table-empty">'
      + '<strong>加载失败</strong><span>' + ATW.escapeHtml(message) + '</span>'
      + '<button class="btn btn-ghost btn-sm" type="button" onclick="location.reload()">重新加载</button>'
      + '</div></td></tr>';
    el('modelSummary').textContent = '加载失败';
  }

  function openCreate() {
    el('modelForm').reset();
    el('modelId').value = '';
    el('modelModalTitle').textContent = '新增模型配置';
    el('configName').value = '';
    el('modelName').value = '';
    el('baseUrl').value = '';
    el('apiKeyEnv').value = 'DEEPSEEK_API_KEY';
    el('chatPath').value = '/chat/completions';
    el('contextBudget').value = '262144';
    el('maxOutputTokens').value = '8192';
    el('connectTimeoutMs').value = '10000';
    el('readTimeoutMs').value = '300000';
    el('webSearchEnabled').checked = false;
    el('webSearchMode').value = 'DEEPSEEK_TOOLS';
    el('remark').value = '';
    el('modelSubmitBtn').textContent = '保存配置';
    AdminPage.openModal('modelModal');
  }

  function openEdit(item) {
    el('modelForm').reset();
    el('modelId').value = String(item.id);
    el('modelModalTitle').textContent = '编辑模型配置';
    el('configName').value = item.configName || '';
    el('modelName').value = item.modelName || '';
    el('baseUrl').value = item.baseUrl || '';
    el('apiKeyEnv').value = item.apiKeyEnv || '';
    el('chatPath').value = item.chatPath || '/chat/completions';
    el('contextBudget').value = String(item.contextBudget);
    el('maxOutputTokens').value = String(item.maxOutputTokens);
    el('connectTimeoutMs').value = String(item.connectTimeoutMs);
    el('readTimeoutMs').value = String(item.readTimeoutMs);
    el('webSearchEnabled').checked = !!item.webSearchEnabled;
    el('webSearchMode').value = item.webSearchMode || 'DEEPSEEK_TOOLS';
    el('remark').value = item.remark || '';
    el('modelSubmitBtn').textContent = '保存修改';
    AdminPage.openModal('modelModal');
  }

  async function submitForm(event) {
    event.preventDefault();
    var id = el('modelId').value;
    var body = {
      configName: el('configName').value.trim(),
      modelName: el('modelName').value.trim(),
      baseUrl: el('baseUrl').value.trim(),
      apiKeyEnv: el('apiKeyEnv').value.trim(),
      chatPath: el('chatPath').value.trim(),
      contextBudget: Number(el('contextBudget').value),
      maxOutputTokens: Number(el('maxOutputTokens').value),
      connectTimeoutMs: Number(el('connectTimeoutMs').value),
      readTimeoutMs: Number(el('readTimeoutMs').value),
      webSearchEnabled: el('webSearchEnabled').checked,
      webSearchMode: el('webSearchMode').value,
      remark: el('remark').value.trim()
    };

    if (!body.configName) {
      focusField('configName', '请输入配置名称');
      return;
    }
    if (!body.modelName) {
      focusField('modelName', '请输入模型名称');
      return;
    }
    if (!/^https?:\/\//i.test(body.baseUrl)) {
      focusField('baseUrl', '接口地址必须以 http:// 或 https:// 开头');
      return;
    }
    if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(body.apiKeyEnv)) {
      focusField('apiKeyEnv', 'API Key 环境变量格式不正确');
      return;
    }
    if (!body.chatPath || body.chatPath.charAt(0) !== '/') {
      focusField('chatPath', '对话路径必须以 / 开头');
      return;
    }
    if (!Number.isFinite(body.contextBudget) || body.contextBudget < 256 || body.contextBudget > 1048576) {
      focusField('contextBudget', '上下文预算必须在 256-1048576 之间');
      return;
    }
    if (!Number.isFinite(body.maxOutputTokens) || body.maxOutputTokens < 1 || body.maxOutputTokens > 262144) {
      focusField('maxOutputTokens', '最大输出 Token 必须在 1-262144 之间');
      return;
    }
    if (body.contextBudget < body.maxOutputTokens) {
      focusField('contextBudget', '上下文预算不能小于最大输出 Token');
      return;
    }
    if (!Number.isFinite(body.connectTimeoutMs) || body.connectTimeoutMs < 1000 || body.connectTimeoutMs > 600000) {
      focusField('connectTimeoutMs', '连接超时必须在 1000-600000 毫秒之间');
      return;
    }
    if (!Number.isFinite(body.readTimeoutMs) || body.readTimeoutMs < 1000 || body.readTimeoutMs > 600000) {
      focusField('readTimeoutMs', '读取超时必须在 1000-600000 毫秒之间');
      return;
    }

    var button = el('modelSubmitBtn');
    ATW.setButtonLoading(button, true, id ? '保存中...' : '创建中...');
    try {
      if (id) {
        await ATW.api('/api/model-configs/' + id, { method: 'PUT', body: body });
        ATW.toast('模型配置已更新', 'success');
      } else {
        await ATW.api('/api/model-configs', { method: 'POST', body: body });
        ATW.toast('模型配置已创建', 'success');
      }
      AdminPage.closeModal('modelModal');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '保存失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  function focusField(id, message) {
    ATW.toast(message, 'error');
    el(id).focus();
  }

  async function testModel(item, button) {
    ATW.setButtonLoading(button, true, '测试中...');
    try {
      var result = await ATW.api('/api/model-configs/' + item.id + '/test', {
        method: 'POST',
        timeout: 45000
      });
      if (result && result.success) {
        ATW.toast(result.message || '连接成功', 'success');
      } else {
        ATW.toast((result && result.message) || '连接失败', 'error');
      }
    } catch (e) {
      ATW.toast(e.message || '测试失败', 'error');
    } finally {
      ATW.setButtonLoading(button, false);
    }
  }

  async function activateModel(item) {
    var confirmed = await AdminPage.confirm({
      title: '启用模型配置',
      message: '启用「' + item.configName + '」后，新对话将使用该模型。确定继续？',
      confirmText: '启用'
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/model-configs/' + item.id + '/activate', { method: 'POST' });
      ATW.toast('模型配置已启用', 'success');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '启用失败', 'error');
    }
  }

  async function removeModel(item) {
    var confirmed = await AdminPage.confirm({
      title: '删除模型配置',
      message: '删除「' + item.configName + '」后无法恢复，确定继续？',
      confirmText: '删除配置',
      danger: true
    });
    if (!confirmed) {
      return;
    }
    try {
      await ATW.api('/api/model-configs/' + item.id, { method: 'DELETE' });
      ATW.toast('模型配置已删除', 'success');
      loadList();
    } catch (e) {
      ATW.toast(e.message || '删除失败', 'error');
    }
  }
})();

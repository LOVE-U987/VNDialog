/* ============================================================
   VNDialog · 对话历史回顾 UI 逻辑
   自适应 WebGUI/MCEF 桥接；脱离游戏时以预览数据独立运行
   ============================================================ */
(function () {
  "use strict";

  // ---------- 桥接抽象（WebGUI / MCEF 或浏览器预览） ----------
  const Bridge = {
    supported: typeof window.webview !== 'undefined' && !!window.webview,
    // postToGame / postMessage：WebGUI 页面→Mod
    postToGame(msg) {
      try {
        if (this.supported && window.webview.postToGame) window.webview.postToGame(msg);
        else if (this.supported && window.webview.sendMessage) window.webview.sendMessage(msg);
        else if (window.postMessage) window.postMessage(JSON.stringify({ source: 'vndialog-history', ...msg }), '*');
        this.status('bridge', `已连接 WebGUI：${JSON.stringify(msg)}`);
      } catch (e) {
        console.error('[VNDialog] postToGame failed', e);
        this.status('error', '桥接发送失败');
      }
    },
    status(kind, text) {
      const el = document.getElementById('bridge-status');
      if (!el) return;
      el.hidden = false;
      el.textContent = text;
    },
    gotData() {
      const el = document.getElementById('bridge-status');
      if (el) el.hidden = true;
    }
  };

  // ---------- DOM 引用 ----------
  const timeline = document.getElementById('timeline');
  const dataEl = document.getElementById('history-data');
  const btnClose = document.getElementById('btn-close');

  // ---------- 数据解析：优先 <script type=json>，其次 window.vndialogHistory ----------
  function fetchData() {
    try {
      const raw = (dataEl && dataEl.textContent.trim()) ? dataEl.textContent : '[]';
      const list = JSON.parse(raw);
      if (Array.isArray(list) && list.length) return list;
    } catch (e) { console.warn('[webBridge] inline JSON 解析失败，回退预览数据', e); }
    if (window.__vndialogPreviewData) return window.__vndialogPreviewData;
    return null;
  }

  // ---------- 类型动画 ----------
  function typewriter(el, text, speed = 14, done) {
    let i = 0;
    const caret = document.createElement('span');
    caret.className = 'caret';
    el.appendChild(caret);
    (function step() {
      if (i < text.length) {
        el.insertBefore(document.createTextNode(text[i++]), caret);
        setTimeout(step, speed);
      } else {
        caret.remove();
        if (typeof done === 'function') done();
      }
    })();
  }

  // ---------- 渲染一条历史记录 ----------
  function createCard(item, index) {
    const card = document.createElement('div');
    card.className = 'history-card';
    card.style.animationDelay = Math.min(index * 70, 600) + 'ms';

    // 立绘（可选）
    let portraitHtml = '';
    if (item.portrait) {
      portraitHtml =
        '<img class="history-portrait" src="' +
        (item.portrait.startsWith('http') ? item.portrait : '/textures/portraits/' + item.portrait) +
        '" alt="立绘" onerror="this.style.visibility=hidden">';
    }

    const speaker = item.speaker || '旁白';
    const time = item.time
      ? new Date(Number(item.time)).toLocaleTimeString('zh-CN', { hour12: false })
      : ('#' + (item.seq ?? (index + 1)));

    const body = document.createElement('div');
    body.className = 'history-body';
    body.innerHTML =
      '<div class="history-speaker">' +
        '<span class="speaker-name"></span>' +
        '<span class="history-speaker-time"></span>' +
      '</div>' +
      '<div class="history-text"></div>';
    body.querySelector('.speaker-name').textContent = speaker;
    body.querySelector('.history-speaker-time').textContent = time;

    card.innerHTML = portraitHtml ? (portraitHtml) : '';
    card.appendChild(body);

    const textEl = body.querySelector('.history-text');
    const txt = item.text || item.message || '';
    // 用打字机逐字显示，增强动效（长文本可整体显示速度）
    const fast = item.typeSpeed === 0; // 0 => 立即显示
    if (fast) textEl.textContent = txt;
    else typewriter(textEl, txt, 24);

    return card;
  }

  // ---------- 渲染 ----------
  function render(list) {
    timeline.innerHTML = '';
    if (!list || !list.length) {
      timeline.innerHTML = '<div class="empty-state">暂无对话记录</div>';
      return;
    }
    const frag = document.createDocumentFragment();
    list.forEach((item, i) => frag.appendChild(createCard(item, i)));
    timeline.appendChild(frag);
  }

  // ---------- 按钮 ----------
  function bindButtons() {
    if (btnClose) btnClose.addEventListener('click', () => Bridge.postToGame({ channel: 'vndialog', action: 'close_history' }));
  }

  // ---------- 启动 ----------
  function boot() {
    Bridge.gotData();
    const data = fetchData();
    render(data);
    bindButtons();

    // 监听 Mod→页面（WebGUI client 事件）
    if (window.addEventListener) {
      window.addEventListener('message', (e) => {
        try {
          const d = typeof e.data === 'string' ? JSON.parse(e.data) : e.data;
          if (d && (d.channel === 'history' || d.type === 'vndialog-history')) {
            if (Array.isArray(d.data)) render(d.data);
          }
        } catch (ignored) {}
      });
    }
    Bridge.status('ready');
  }

  // 挂到全局便于外部调用
  window.VNDialogHistory = { render, Bridge };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
/* ============================================================
   点歌台 · 页面外壳
   顶部 tab + 手机端底栏 + 右缘小箭头呼出的「正在听 / 播放列表」侧栏。
   玩家进来不需要登录：能用真实服务就直接连，连不上自动进试听模式。
   ============================================================ */
(function () {
  'use strict';
  var SI = window.SI;

  var ICON = {
    play:     '<path d="M8 5.5v13l11-6.5z"/>',
    pause:    '<path d="M9 5v14M15 5v14"/>',
    search:   '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
    heart:    '<path d="M20.8 5.6a5.2 5.2 0 0 0-7.4 0L12 7l-1.4-1.4a5.2 5.2 0 0 0-7.4 7.4L12 21l8.8-8a5.2 5.2 0 0 0 0-7.4z"/>',
    chat:     '<path d="M21 11.5a8.5 8.5 0 0 1-12.2 7.7L4 20l.8-4.8A8.5 8.5 0 1 1 21 11.5z"/>',
    settings: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
    plus:     '<path d="M12 5v14M5 12h14"/>',
    note:     '<circle cx="7" cy="18" r="3"/><circle cx="18" cy="16" r="3"/><path d="M10 18V5l11-2v13"/>',
    sakura:   '<path d="M12 2.8c1.9 0 3 1.9 2.6 3.8 1.9-1 4.3-.5 4.8 1.4.5 1.9-.9 3.4-2.8 3.9 1.9.5 3.3 2 2.8 3.9-.5 1.9-2.9 2.4-4.8 1.4.4 1.9-.7 3.8-2.6 3.8s-3-1.9-2.6-3.8c-1.9 1-4.3.5-4.8-1.4-.5-1.9.9-3.4 2.8-3.9-1.9-.5-3.3-2-2.8-3.9.5-1.9 2.9-2.4 4.8-1.4C9 4.7 10.1 2.8 12 2.8z"/><circle cx="12" cy="12" r="2.2"/>',
    users:    '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 5.2a3.5 3.5 0 0 1 0 6.6M17.5 14.5a6.5 6.5 0 0 1 4 5.5"/>',
    refresh:  '<path d="M20 11a8 8 0 1 0-2.3 6.3M20 5v6h-6"/>',
    download: '<path d="M12 3v12M7 11l5 5 5-5M4 20h16"/>',
    copy:     '<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M15 9V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h4"/>',
    send:     '<path d="M21 3L3 10.5l7 2.5 2.5 7L21 3z"/><path d="M10 13L21 3"/>',
    sun:      '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    moon:     '<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4a8.5 8.5 0 1 0 10.5 10.5z"/>',
    qr:       '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3h-3zM20 14h1M14 20h3M20 17v4"/>',
    shield:   '<path d="M12 3l8 3v6c0 5-3.4 8-8 9-4.6-1-8-4-8-9V6l8-3z"/><path d="M9 12l2 2 4-4"/>',
    check:    '<path d="M4 12.5l5 5L20 6.5"/>',
    clock:    '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3.5 2"/>',
    sparkle:  '<path d="M12 2l1.8 6.2L20 10l-6.2 1.8L12 18l-1.8-6.2L4 10l6.2-1.8L12 2z"/>',
    list:     '<path d="M9 6h12M9 12h12M9 18h12"/><circle cx="4" cy="6" r="1"/><circle cx="4" cy="12" r="1"/><circle cx="4" cy="18" r="1"/>',
    external: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
    chevron:  '<path d="M9 6l6 6-6 6"/>',
    chevronLeft: '<path d="M15 6l-6 6 6 6"/>'
  };
  function svg(name) {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + (ICON[name] || '') + '</svg>';
  }
  SI.icon = svg;

  /* 主导航：4 个玩家页。接口调试台只从设置进入。 */
  var NAV = [
    { page: 'request',  href: 'index.html',    label: '点歌', icon: 'search' },
    { page: 'chat',     href: 'chat.html',     label: '聊天', icon: 'chat' },
    { page: 'settings', href: 'settings.html', label: '设置', icon: 'settings' }
  ];
  var TITLES = { request: '点歌', chat: '聊天', settings: '设置', api: '接口调试台' };
  SI.navTitle = function (page) { return TITLES[page] || '点歌台'; };

  /* -------------------------------------------------- 自动接入
     服主只要把带 ?api=...&token=... 的链接发到群里，玩家点开就直接连上。
     没有任何参数时进入试听模式，不要求登录、不跳转。 */
  function autoConnect() {
    var api = SI.param('api'), token = SI.param('token'), user = SI.param('user');
    if (api) {
      var patch = { baseUrl: api, demo: false };
      if (token) patch.token = token;
      if (user) patch.username = user;
      SI.set(patch);
    }
    if (SI.state.demo) {
      var p = {};
      if (!SI.state.username) p.username = 'DRa6a';
      if (!SI.state.token) p.token = 'demo_session';
      if (Object.keys(p).length) SI.set(p);
    }
  }

  /* 飘落的花瓣：纯装饰，不接收点击、不挡内容，尊重减少动效偏好 */
  function addPetals() {
    if (SI.qs('#petalLayer')) return;
    var layer = document.createElement('div');
    layer.className = 'petal-layer';
    layer.id = 'petalLayer';
    var h = '';
    for (var i = 0; i < 12; i++) {
      h += '<span class="petal" style="left:' + (2 + i * 8.4).toFixed(1) + '%;' +
        'animation-delay:-' + (i * 1.6).toFixed(1) + 's;' +
        'animation-duration:' + (10 + (i % 5) * 2) + 's;' +
        '--sway:' + (i % 2 ? 1 : -1) + '"></span>';
    }
    layer.innerHTML = h;
    if (document.body && document.body.appendChild) document.body.appendChild(layer);
  }

  function renderShell() {
    var page = document.body.dataset.page;

    var header = SI.qs('#appHeader');
    if (header) {
      var tabs = NAV.map(function (it) {
        return '<a href="' + it.href + '" class="' + (it.page === page ? 'active' : '') + '">' + it.label + '</a>';
      }).join('');
      header.innerHTML =
        '<a class="brand" href="index.html">' +
          '<span class="brand-mark">' + svg('sakura') + '</span>' +
          '<span><span class="brand-name">点歌台</span><br><span class="brand-sub">SAKURA · 一起听点好的</span></span>' +
        '</a>' +
        '<nav class="top-tabs">' + tabs + '</nav>' +
        '<div class="header-right">' +
          '<a class="status-pill" id="statusPill" href="settings.html" title="查看连接状态">' +
            '<span class="status-dot"></span><span id="statusText">连接中…</span>' +
          '</a>' +
          '<button class="icon-btn hide-sm" id="btnTheme" title="切换明暗">' + svg(SI.state.theme === 'light' ? 'moon' : 'sun') + '</button>' +
          '<span class="user-chip" id="userChip"></span>' +
        '</div>';

      var btn = SI.qs('#btnTheme');
      if (btn) btn.addEventListener('click', function () {
        SI.toggleTheme();
        this.innerHTML = svg(SI.state.theme === 'light' ? 'moon' : 'sun');
      });
    }

    var bottom = SI.qs('#bottomNav');
    if (bottom) {
      bottom.innerHTML = NAV.map(function (it) {
        return '<a href="' + it.href + '" class="' + (it.page === page ? 'active' : '') + '">' +
          svg(it.icon) + '<span>' + it.label + '</span></a>';
      }).join('');
    }
  }

  /* -------------------------------------------------- 连接状态 */
  async function refreshHeader() {
    var pill = SI.qs('#statusPill'), text = SI.qs('#statusText'), chip = SI.qs('#userChip');
    try {
      var r = await SI.api('/api/me');
      var d = r.data || {};
      SI.state.user = d.user || null;
      SI.state.server = d.server || null;
      if (d.user && d.user.name) { SI.state.username = d.user.name; SI.save(); }
      if (chip) chip.innerHTML = SI.avatar(SI.state.username || '?', d.user && d.user.avatar) +
        '<span>' + SI.esc(SI.state.username || '玩家') + '</span>';
      if (pill && text) {
        if (SI.state.demo) {
          pill.className = 'status-pill demo';
          text.textContent = '试听模式 · ' + ((d.server && d.server.online) || 0) + ' 人在听';
        } else {
          pill.className = 'status-pill good';
          text.textContent = '已连上 · ' + ((d.server && d.server.online) || 0) + ' 人在线';
        }
      }
      SI.emit('connected', d);
    } catch (e) {
      if (chip) chip.innerHTML = SI.avatar(SI.state.username || '?', null) +
        '<span>' + SI.esc(SI.state.username || '玩家') + '</span>';
      if (pill && text) {
        pill.className = 'status-pill bad';
        text.textContent = SI.state.demo ? '试听模式' : '还没连上服务器';
      }
      SI.emit('disconnected', e);
    }
  }
  SI.refreshHeader = refreshHeader;

  /* 播放给谁：玩家语言，不出现 target / broadcast 这类词 */
  SI.fillTargets = async function (select, mode) {
    if (!select) return;
    var chat = mode === 'chat';
    var fallback = SI.state.target || (chat ? '__all__' : 'self');
    try {
      var r = await SI.api('/api/players');
      var players = (r.data && r.data.players) || [];
      var h = '<option value="self">' + (chat ? '只发给我自己' : '只放给我自己听') + '</option>';
      players.forEach(function (p) {
        if (p.self) return;
        h += '<option value="' + SI.esc(p.name) + '">' + (chat ? '发给 ' : '放给 ') + SI.esc(p.name) +
          (chat || p.hasCu ? '' : '（可能收不到）') + '</option>';
      });
      if (!r.data || r.data.allowBroadcast !== false) {
        h += '<option value="__all__">' + (chat ? '全服都能看见' : '让全服一起听') + '</option>';
      }
      select.innerHTML = h;
      if (Array.prototype.some.call(select.options, function (o) { return o.value === fallback; })) select.value = fallback;
    } catch (e) {
      select.innerHTML = '<option value="self">' + (chat ? '只发给我自己' : '只放给我自己听') +
        '</option><option value="__all__">' + (chat ? '全服都能看见' : '让全服一起听') + '</option>';
      select.value = chat ? '__all__' : 'self';
    }
    select.onchange = function () { SI.set({ target: select.value }); SI.emit('target', select.value); };
  };

  /* ============================================================
     隐藏侧栏：正在听 + 播放列表
     数据来源：/api/entries（服务器当前播放 + 排队），本机播放器状态来自 player.js。
     ============================================================ */
  var serverCur = null, autoOpened = false;

  function durationOf(title) {
    var songs = (SI.mock && SI.mock.db && SI.mock.db.songs) || [];
    var hit = songs.filter(function (s) { return s.name === title; })[0];
    return (hit && hit.duration) || 240000;
  }
  function setSideOpen(v) {
    var panel = SI.qs('#sidePanel'), toggle = SI.qs('#sideToggle'), mask = SI.qs('#sideMask');
    if (panel) panel.classList.toggle('open', v);
    if (toggle) toggle.classList.toggle('open', v);
    if (mask) mask.classList.toggle('show', v);
    try { localStorage.setItem('si_side_open_v1', v ? '1' : '0'); } catch (e) {}
  }
  SI.sideOpen = setSideOpen;

  function renderSideShell() {
    var root = SI.qs('#sideRoot');
    if (!root) return;
    root.innerHTML =
      '<button class="side-toggle" id="sideToggle" title="正在听 / 播放列表">' + svg('chevronLeft') + '</button>' +
      '<div class="side-mask" id="sideMask"></div>' +
      '<aside class="side-panel" id="sidePanel">' +
        '<div class="side-head"><div><div class="t">正在听</div>' +
          '<div class="s">服务器播放状态 · 播放列表</div></div>' +
          '<span class="spacer"></span><button class="icon-btn" id="sideClose">✕</button></div>' +
        '<div class="side-scroll">' +
          '<div class="side-section"><div class="side-label">此刻在放</div><div id="sideNow"></div></div>' +
          '<div class="side-section"><div class="side-label">播放列表 ' +
            '<span id="sideQueueCount" class="dim"></span></div>' +
            '<div class="side-queue" id="sideQueue"></div></div>' +
          '<div class="side-section"><div class="side-label">最近点歌 ' +
            '<span id="sideHistoryCount" class="dim"></span></div>' +
            '<label class="toggle" style="margin-bottom:10px"><input type="checkbox" id="sideMineOnly">' +
            '<span class="track"></span><span class="small">只看我点的</span></label>' +
            '<div class="side-queue" id="sideHistory"></div></div>' +
        '</div>' +
      '</aside>';

    var toggle = SI.qs('#sideToggle'), panel = SI.qs('#sidePanel'), mask = SI.qs('#sideMask');
    if (toggle) toggle.addEventListener('click', function () {
      setSideOpen(!(panel && panel.classList.contains('open')));
    });
    if (mask) mask.addEventListener('click', function () { setSideOpen(false); });
    var close = SI.qs('#sideClose');
    if (close) close.addEventListener('click', function () { setSideOpen(false); });
    var mineOnly = SI.qs('#sideMineOnly');
    if (mineOnly) mineOnly.addEventListener('change', function () { loadSide(); });
    try { if (localStorage.getItem('si_side_open_v1') === '1') setSideOpen(true); } catch (e) {}
  }

  function renderSideNow() {
    var host = SI.qs('#sideNow');
    if (!host) return;
    var wp = SI.webPlayerState ? SI.webPlayerState() : { song: null, playing: false, volume: .7 };
    var useWeb = !!wp.song;
    var song = useWeb ? wp.song :
      (serverCur ? { name: serverCur.title, artist: serverCur.artist } : null);

    if (!song) {
      host.innerHTML = '<div class="empty" style="padding:28px 10px"><span class="big">♪</span>' +
        '现在没有歌在放<p>去点一首，侧栏会显示正在听</p></div>';
      return;
    }

    var dur = useWeb ? (wp.duration || 240000) : durationOf(song.name);
    var elapsed = useWeb ? (wp.elapsed || 0) :
      Math.min(dur, Math.max(0, Date.now() - (serverCur.requestedAt || Date.now())));
    var pct = Math.min(100, elapsed / dur * 100).toFixed(1);

    var tag = useWeb
      ? '<span class="badge hot">本机播放中</span>'
      : '<span class="badge ok">服务器正在播</span>';
    var note = useWeb
      ? SI.esc(wp.error || wp.reason || wp.source || '在这台设备上播放')
      : ((serverCur.listeners || 1) + ' 人在听 · ' +
         (serverCur.mine ? '你点的' : SI.esc(serverCur.player || '有人') + ' 点的'));

    host.innerHTML = '<div class="np-card">' +
      '<div class="np-top">' + SI.cover(song, '') +
        '<div class="grow"><div class="n">' + SI.esc(song.name) + '</div>' +
        '<div class="a">' + SI.esc(song.artist || '未知歌手') + '</div></div></div>' +
      '<div class="np-source row wrap" style="gap:8px">' + tag +
        '<span class="small dim">' + note + '</span></div>' +
      '<div class="player-progress">' +
        '<div class="progress-track" id="sideSeek"><div class="progress-fill" style="width:' + pct + '%"></div></div>' +
        '<div class="progress-meta"><span>' + SI.fmtDur(elapsed) + '</span><span>' + SI.fmtDur(dur) + '</span></div>' +
      '</div>' +
      '<div class="player-ctrl">' +
        '<button class="btn btn-primary btn-round" id="sidePlay" title="播放 / 暂停">' +
          svg(useWeb && wp.playing ? 'pause' : 'play') + '</button>' +
        (useWeb ? '<button class="btn btn-sm btn-ghost" id="sideStop">停止</button>' : '') +
        '<span class="grow"></span>' +
        '<input type="range" class="vol" id="sideVol" min="0" max="100" value="' +
          Math.round((wp.volume || .7) * 100) + '" title="音量">' +
      '</div></div>';

    var play = SI.qs('#sidePlay');
    if (play) play.addEventListener('click', function () {
      if (wp.song) SI.webToggle();
      else SI.webPlay(song, '本机试听');
    });
    var stop = SI.qs('#sideStop');
    if (stop) stop.addEventListener('click', function () { SI.webStop(); });
    var vol = SI.qs('#sideVol');
    if (vol) vol.addEventListener('input', function () { SI.webVolume(Number(this.value) / 100); });
    var seek = SI.qs('#sideSeek');
    if (seek) seek.addEventListener('click', function (e) {
      var box = this.getBoundingClientRect ? this.getBoundingClientRect() : { left: 0, width: 1 };
      SI.webSeek((e.clientX - box.left) / (box.width || 1));
    });
  }

  async function loadSide() {
    try {
      var r = await SI.api('/api/entries');
      var entries = (r.data && r.data.entries) || [];
      /* 真实接口 playing 是「播放中的客户端数」(int)，演示层早期是布尔，两者都兼容 */
      serverCur = entries.filter(function (e) {
        return e.state === 'PLAYING' || Number(e.playing) > 0;
      })[0] || null;
      var queue = entries.filter(function (e) { return e.state === 'QUEUED'; })
        .sort(function (a, b) { return a.requestedAt - b.requestedAt; });

      var qh = SI.qs('#sideQueue');
      if (qh) qh.innerHTML = queue.length ? queue.map(function (e, i) {
        return '<div class="sq-item"><span class="sq-num">' + (i + 1) + '</span>' +
          SI.cover({ name: e.title, artist: e.artist }, '') +
          '<div class="grow"><div class="n">' + SI.esc(e.title) + '</div>' +
          '<div class="m">' + SI.esc(e.artist) + ' · ' +
          SI.esc(e.mine ? '你点的' : ((e.player || '有人') + ' 点的')) + '</div></div></div>';
      }).join('') : '<div class="empty" style="padding:22px 8px"><span class="big">♪</span>队列是空的</div>';
      var qc = SI.qs('#sideQueueCount');
      if (qc) qc.textContent = queue.length ? '· ' + queue.length + ' 首' : '';

      /* 原版网页的「点歌记录」并入侧栏，支持只看我点的 */
      var HIST = {
        QUEUED: ['排队中', 'info'], PLAYING: ['正在放', 'ok'], DONE: ['放完了', 'ok'],
        SKIPPED: ['被切掉了', 'warn'], OFFLINE: ['没放成', 'warn'], FAILED: ['没能播放', 'danger']
      };
      var onlyMine = SI.qs('#sideMineOnly');
      var wantMine = !!(onlyMine && onlyMine.checked);
      var hist = entries.filter(function (e) {
        return !wantMine || e.mine || e.player === SI.state.username;
      }).slice(0, 8);
      var hh = SI.qs('#sideHistory');
      if (hh) hh.innerHTML = hist.length ? hist.map(function (e) {
        var st = HIST[e.state] || [e.state || '—', ''];
        return '<div class="sq-item">' + SI.cover({ name: e.title, artist: e.artist }, '') +
          '<div class="grow"><div class="n">' + SI.esc(e.title) + '</div>' +
          '<div class="m">' + SI.esc(e.artist) + ' · ' +
          SI.esc(e.mine ? '我' : (e.player || '有人')) + ' · ' + SI.ago(e.requestedAt) + '</div></div>' +
          '<span class="badge ' + st[1] + '">' + st[0] + '</span></div>';
      }).join('') : '<div class="empty" style="padding:22px 8px"><span class="big">♫</span>还没有点歌记录</div>';
      var hc = SI.qs('#sideHistoryCount');
      if (hc) hc.textContent = hist.length ? '· ' + hist.length + ' 条' : '';
      renderSideNow();
    } catch (e) {
      var host = SI.qs('#sideNow');
      if (host) host.innerHTML = '<div class="empty" style="padding:24px 8px"><span class="big">!</span>暂时读不到播放状态</div>';
    }
  }
  SI.loadSide = loadSide;
  SI.sideRefreshNow = renderSideNow;

  document.addEventListener('DOMContentLoaded', function () {
    SI.applyTheme();
    addPetals();
    autoConnect();
    renderShell();
    renderSideShell();
    refreshHeader();
    loadSide();
    SI.poll('side', function () { return loadSide(); }, 6000);
    SI.on('web-player', function (s) {
      renderSideNow();
      if (s && s.playing && s.song && !autoOpened) { autoOpened = true; SI.sideOpen(true); }
    });
    SI.emit('ready');
  });
  window.addEventListener('beforeunload', function () { SI.stopAllPolls(); });
})();

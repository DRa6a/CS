/* ============================================================
   Si Console · core
   状态、API 客户端、通用工具。所有页面共用，classic script（可 file:// 直接打开）。
   ============================================================ */
(function () {
  'use strict';

  var KEY = 'si_console_state_v2';
  var DEFAULTS = {
    baseUrl: 'http://127.0.0.1:8765',
    token: '',
    username: '',
    user: null,
    server: null,
    demo: true,
    theme: 'light',
    target: 'self',
    autoRefresh: true,
    simulateOffline: false,
    /* 点歌音源：server=跟随服务器(si.json 的 musicSource) / netease / external / auto */
    musicSource: 'server'
  };

  function load() {
    try {
      var raw = localStorage.getItem(KEY);
      var saved = raw ? JSON.parse(raw) : {};
      var out = {};
      Object.keys(DEFAULTS).forEach(function (k) {
        out[k] = Object.prototype.hasOwnProperty.call(saved, k) ? saved[k] : DEFAULTS[k];
      });
      return out;
    } catch (e) { return Object.assign({}, DEFAULTS); }
  }

  var state = load();
  var etags = {};
  var polls = {};
  var requestLog = [];

  var SI = {
    state: state,
    log: requestLog,
    version: '1.0.0'
  };
  window.SI = SI;

  SI.save = function () {
    try { localStorage.setItem(KEY, JSON.stringify(state)); } catch (e) {}
  };
  SI.set = function (patch) { Object.assign(state, patch); SI.save(); };
  SI.reset = function () { Object.assign(state, DEFAULTS); SI.save(); };
  SI.apiBase = function () { return String(state.baseUrl || '').replace(/\/+$/, ''); };

  /* ------------------------------------------------------ errors */
  function ApiError(status, message, data) {
    this.name = 'ApiError';
    this.status = status;
    this.message = message;
    this.data = data || null;
  }
  ApiError.prototype = Object.create(Error.prototype);
  SI.ApiError = ApiError;

  /* ------------------------------------------------------ api client */
  /**
   * 调 Si 的 /api/* 接口。真实模式走 fetch + Bearer + ETag；演示模式交给 mock。
   * 返回 { ok, data, status, ms, notModified }，失败抛 ApiError。
   */
  SI.api = async function (path, opts) {
    opts = opts || {};
    var method = opts.method || 'GET';
    var body = opts.body;
    var params = opts.params || {};
    var auth = opts.auth !== false;
    var url = path;

    var usp = new URLSearchParams();
    Object.keys(params).forEach(function (k) {
      var v = params[k];
      if (v !== undefined && v !== null && v !== '') usp.set(k, v);
    });
    var qs = usp.toString();
    if (qs) url += (url.indexOf('?') >= 0 ? '&' : '?') + qs;

    var started = performance.now();
    var result;

    if (state.demo) {
      result = await SI.mock.handle(path, { method: method, body: body, params: params, url: url });
    } else {
      var headers = {};
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      if (auth && state.token) headers['Authorization'] = 'Bearer ' + state.token;
      if (method === 'GET' && opts.etag !== false && etags[url]) headers['If-None-Match'] = etags[url];

      var res;
      try {
        res = await fetch(SI.apiBase() + url, {
          method: method,
          headers: headers,
          body: body === undefined ? undefined : JSON.stringify(body),
          credentials: 'omit'
        });
      } catch (e) {
        throw new ApiError(0, '连不上 ' + SI.apiBase() + '：' + (e.message || '网络错误') + '（确认服务已启动、端口放行、corsOrigins 已配置）');
      }
      if (res.status === 304) {
        result = { ok: true, data: null, status: 304, ms: Math.round(performance.now() - started), notModified: true };
      } else {
        var tag = res.headers.get('ETag');
        if (tag) etags[url] = tag;
        var data = null;
        try { data = await res.json(); }
        catch (e) { data = { ok: false, error: '响应不是 JSON（HTTP ' + res.status + '）' }; }
        result = { ok: res.ok, data: data, status: res.status, ms: Math.round(performance.now() - started) };
      }
    }

    requestLog.unshift({
      at: Date.now(), method: method, url: url, status: result.status,
      ms: result.ms, request: body === undefined ? null : body, response: result.data
    });
    if (requestLog.length > 60) requestLog.length = 60;
    SI.emit('request', requestLog[0]);

    if (!result.ok) {
      var msg = (result.data && result.data.error) || ('HTTP ' + result.status);
      throw new ApiError(result.status, msg, result.data);
    }
    return result;
  };

  /* ------------------------------------------------------ tiny event bus */
  var listeners = {};
  SI.on = function (evt, fn) { (listeners[evt] = listeners[evt] || []).push(fn); };
  SI.emit = function (evt, payload) { (listeners[evt] || []).forEach(function (f) { try { f(payload); } catch (e) {} }); };

  /* ------------------------------------------------------ polling */
  SI.poll = function (key, fn, ms) {
    SI.stopPoll(key);
    var alive = true, timer = null;
    var tick = async function () {
      if (!alive) return;
      try { await fn(); } catch (e) {}
      if (alive) timer = setTimeout(tick, ms);
    };
    polls[key] = { stop: function () { alive = false; if (timer) clearTimeout(timer); } };
    tick();
  };
  SI.stopPoll = function (key) { if (polls[key] && polls[key].stop) polls[key].stop(); delete polls[key]; };
  SI.stopAllPolls = function () { Object.keys(polls).forEach(SI.stopPoll); };

  /* ------------------------------------------------------ utils */
  SI.qs = function (sel, root) { return (root || document).querySelector(sel); };
  SI.qsa = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };
  SI.esc = function (s) {
    return String(s === undefined || s === null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  };
  SI.fmtDur = function (ms) {
    if (!ms || ms < 0) return '—';
    var t = Math.floor(ms / 1000), m = Math.floor(t / 60), s = t % 60;
    return m + ':' + (s < 10 ? '0' : '') + s;
  };
  SI.fmtTime = function (at, withDate) {
    if (!at) return '—';
    var d = new Date(at), p = function (n) { return n < 10 ? '0' + n : '' + n; };
    var t = p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
    return withDate ? (d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + t) : t;
  };
  SI.ago = function (at) {
    if (!at) return '—';
    var s = Math.max(0, Math.floor((Date.now() - at) / 1000));
    if (s < 60) return s + ' 秒前';
    if (s < 3600) return Math.floor(s / 60) + ' 分钟前';
    if (s < 86400) return Math.floor(s / 3600) + ' 小时前';
    return Math.floor(s / 86400) + ' 天前';
  };
  SI.fmtNum = function (n) {
    n = Number(n) || 0;
    if (n >= 100000000) return (n / 100000000).toFixed(1) + ' 亿';
    if (n >= 10000) return (n / 10000).toFixed(1) + ' 万';
    return String(n);
  };
  SI.copy = function (text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text).then(function () { SI.toast('已复制到剪贴板'); });
    }
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.position = 'fixed'; ta.style.opacity = '0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); SI.toast('已复制到剪贴板'); }
    catch (e) { SI.toast('复制失败，请手动选中', 'error'); }
    document.body.removeChild(ta);
    return Promise.resolve();
  };
  SI.download = function (name, text) {
    var blob = new Blob([text], { type: 'application/json;charset=utf-8' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob); a.download = name;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 1000);
  };
  SI.debounce = function (fn, ms) {
    var t; return function () {
      var args = arguments, self = this;
      clearTimeout(t); t = setTimeout(function () { fn.apply(self, args); }, ms);
    };
  };
  SI.sleep = function (ms) { return new Promise(function (r) { setTimeout(r, ms); }); };
  SI.param = function (name) { return new URLSearchParams(location.search).get(name); };
  SI.initial = function (name) { return (String(name || '?').trim()[0] || '?').toUpperCase(); };
  SI.hue = function (name) {
    var h = 0, s = String(name || 'x');
    for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  };
  SI.avatar = function (name, url, cls) {
    var h = SI.hue(name);
    var h1 = 300 + (h % 56), h2 = (h1 + 42) % 360;
    var bg = 'linear-gradient(140deg, hsl(' + h1 + ',54%,56%), hsl(' + h2 + ',52%,46%))';
    var letter = '<span style="position:absolute;inset:0;display:grid;place-items:center">' + SI.esc(SI.initial(name)) + '</span>';
    var img = (!url || state.demo) ? '' :
      '<img src="' + SI.esc(url) + '" alt="' + SI.esc(name) + '" style="position:absolute;inset:0;width:100%;height:100%;object-fit:cover" onerror="this.remove()">';
    return '<span class="avatar ' + (cls || '') + '" style="background:' + bg + ';position:relative;overflow:hidden">' + letter + img + '</span>';
  };
  /* ------------------------------------------------------ 专辑封面
     /api/search、/api/playlist、/api/liked 的歌曲带 cover（真实封面 URL）；
     /api/entries 不带，所以把见过的封面按「歌名|歌手」缓存下来回填。
     缓存也没有时，用离线 SVG 生成一张抽象专辑封面，绝不出现空白方块。 */
  var COVER_KEY = 'si_cover_cache_v1';
  var coverCache = {};
  try { coverCache = JSON.parse(localStorage.getItem(COVER_KEY) || '{}') || {}; } catch (e) { coverCache = {}; }

  SI.rememberCovers = function (songs) {
    (songs || []).forEach(function (s) {
      if (s && s.cover && (s.name || s.title)) {
        coverCache[String(s.name || s.title) + '|' + String(s.artist || '')] = s.cover;
      }
    });
    try { localStorage.setItem(COVER_KEY, JSON.stringify(coverCache)); } catch (e) {}
  };
  SI.coverFor = function (title, artist) {
    return coverCache[String(title || '') + '|' + String(artist || '')] ||
           coverCache[String(title || '') + '|'] || '';
  };
  var PALETTES = [
    ['#FFD6E7', '#C9A7EE'], ['#FFC9D8', '#B9A4F0'], ['#FFE0EA', '#D3A7DC'],
    ['#F9C9E0', '#A9B4F2'], ['#FFD2C9', '#D7A8E8'], ['#F7CBE6', '#BBA0ED']
  ];
  function svgCover(name) {
    var h = SI.hue(name + 'cover');
    var p = PALETTES[h % PALETTES.length];
    var svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200">' +
      '<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
      '<stop offset="0" stop-color="' + p[0] + '"/><stop offset="1" stop-color="' + p[1] + '"/>' +
      '</linearGradient></defs>' +
      '<rect width="200" height="200" fill="url(#g)"/>' +
      '<circle cx="152" cy="46" r="64" fill="#ffffff" fill-opacity=".26"/>' +
      '<circle cx="38" cy="172" r="48" fill="#ffffff" fill-opacity=".18"/>' +
      '<path d="M56 34c15 0 24 15 19 30-5 13-20 19-33 12-12-7-14-24-3-34 5-5 11-8 17-8z" fill="#ffffff" fill-opacity=".22"/>' +
      '<path d="M138 132c13 0 21 12 17 25-4 11-17 16-28 10-10-6-12-20-2-28 4-5 9-7 13-7z" fill="#ffffff" fill-opacity=".16"/>' +
      '<text x="100" y="128" text-anchor="middle" font-size="64" font-weight="800" ' +
      'font-family="PingFang SC,Microsoft YaHei,sans-serif" fill="#6B3450" fill-opacity=".68">' +
      SI.esc(SI.initial(name)) + '</text></svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }
  SI.svgCover = svgCover;

  /**
   * source 可以是歌名字符串，也可以是 {name|title, artist, cover}。
   * 优先用真实封面；没有则用封面缓存；再没有则用离线 SVG 封面。
   */
  SI.cover = function (source, cls) {
    var obj = typeof source === 'string' ? null : (source || {});
    var name = obj ? (obj.name || obj.title || '?') : (source || '?');
    var artist = obj ? (obj.artist || '') : '';
    var url = obj ? (obj.cover || SI.coverFor(name, artist)) : SI.coverFor(name, '');
    if (!url) url = svgCover(name);
    var h = SI.hue(name + 'cover'), p = PALETTES[h % PALETTES.length];
    var bg = 'linear-gradient(145deg,' + p[0] + ',' + p[1] + ')';
    return '<div class="cover ' + (cls || '') + '" style="background:' + bg + '">' +
      '<span class="cover-fallback">' + SI.esc(SI.initial(name)) + '</span>' +
      '<img src="' + SI.esc(url) + '" alt="' + SI.esc(name) + ' 专辑封面" loading="lazy" ' +
      'onerror="this.remove()"></div>';
  };

  /* ------------------------------------------------------ toast */
  SI.toast = function (message, type) {
    var host = SI.qs('#toast-host');
    if (!host) return;
    var el = document.createElement('div');
    el.className = 'toast ' + (type || '');
    el.textContent = message;
    host.appendChild(el);
    setTimeout(function () {
      el.style.transition = '.25s'; el.style.opacity = '0'; el.style.transform = 'translateY(8px)';
      setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 260);
    }, type === 'error' ? 4200 : 2800);
  };

  /* ------------------------------------------------------ domain maps */
  SI.ENTRY_STATE = {
    QUEUED:  { label: '排队中', cls: 'info' },
    PLAYING: { label: '播放中', cls: 'ok' },
    DONE:    { label: '已播完', cls: 'ok' },
    SKIPPED: { label: '已切歌', cls: 'warn' },
    OFFLINE: { label: '已离线', cls: 'warn' },
    FAILED:  { label: '失败',   cls: 'danger' }
  };
  SI.SONG_STATE = {
    OK:      { label: '可播放', cls: 'ok' },
    LOGIN:   { label: 'VIP · 需绑定', cls: 'warn' },
    NONE:    { label: '无版权', cls: 'danger' },
    UNKNOWN: { label: '未知', cls: '' }
  };
  SI.EVENT_TYPE = {
    chat:    { label: '聊天',   cls: 'info' },
    web:     { label: '网页',   cls: 'ok' },
    join:    { label: '进服',   cls: 'ok' },
    quit:    { label: '离服',   cls: '' },
    death:   { label: '死亡',   cls: 'danger' },
    command: { label: '命令',   cls: 'warn' }
  };

  /* ------------------------------------------------------ endpoint catalog（API 调试台用） */
  SI.ENDPOINTS = [
    { group: '系统 / 账号', method: 'GET',  path: '/api/health',            desc: '连通性探测，无需登录', params: [], auth: false },
    { group: '系统 / 账号', method: 'POST', path: '/api/login',             desc: '玩家名 + 密码，换 token', params: [], body: { username: 'DRa6a', password: '' }, auth: false },
    { group: '系统 / 账号', method: 'POST', path: '/api/logout',            desc: '注销当前会话', params: [], auth: true },
    { group: '系统 / 账号', method: 'GET',  path: '/api/me',                desc: '登录状态 + 服务器信息', params: [], auth: true },
    { group: '系统 / 账号', method: 'POST', path: '/api/password',          desc: '修改网页密码', params: [], body: { oldPassword: '', newPassword: '' }, auth: true },
    { group: '网易云',      method: 'GET',  path: '/api/netease/status',    desc: '绑定状态', params: [], auth: true },
    { group: '网易云',      method: 'POST', path: '/api/netease/qr',        desc: '申请扫码登录二维码', params: [], auth: true },
    { group: '网易云',      method: 'GET',  path: '/api/netease/qr/poll',   desc: '轮询扫码状态', params: [{ k: 'key', v: '' }], auth: true },
    { group: '网易云',      method: 'POST', path: '/api/netease/login',     desc: '手机号登录', params: [], body: { phone: '', password: '' }, auth: true },
    { group: '网易云',      method: 'POST', path: '/api/netease/cookie',    desc: '用 MUSIC_U 绑定', params: [], body: { musicU: '' }, auth: true },
    { group: '网易云',      method: 'POST', path: '/api/netease/logout',    desc: '解除绑定', params: [], auth: true },
    { group: '玩家 / 音乐', method: 'GET',  path: '/api/players',           desc: '在线玩家（带 ETag）', params: [], auth: true },
    { group: '玩家 / 音乐', method: 'GET',  path: '/api/search',            desc: '搜索网易云', params: [{ k: 'q', v: '周杰伦' }], auth: true },
    { group: '玩家 / 音乐', method: 'GET',  path: '/api/playlists',         desc: '搜索歌单', params: [{ k: 'q', v: '华语' }, { k: 'limit', v: '20' }], auth: true },
    { group: '玩家 / 音乐', method: 'GET',  path: '/api/playlist',          desc: '歌单详情', params: [{ k: 'id', v: '3778678' }, { k: 'limit', v: '100' }], auth: true },
    { group: '玩家 / 音乐', method: 'GET',  path: '/api/liked',             desc: '我喜欢的音乐（需绑定）', params: [{ k: 'limit', v: '100' }, { k: 'offset', v: '0' }], auth: true },
    { group: '点歌 / 消息', method: 'POST', path: '/api/request',           desc: '点歌，推给目标客户端（可选 source 选择音源）', params: [], body: { id: 0, name: '', artist: '', url: 'https://example.com/song.mp3', target: 'self', source: 'auto' }, auth: true },
    { group: '点歌 / 消息', method: 'GET',  path: '/api/entries',           desc: '最近点歌与状态（ETag）', params: [], auth: true },
    { group: '点歌 / 消息', method: 'POST', path: '/api/message',           desc: '发消息进游戏聊天栏', params: [], body: { target: 'self', text: '大家好' }, auth: true },
    { group: '点歌 / 消息', method: 'GET',  path: '/api/chat',              desc: '游戏内对话（ETag）', params: [], auth: true },
    { group: '点歌 / 消息', method: 'GET',  path: '/api/events',            desc: '事件流，支持增量 since', params: [{ k: 'since', v: '0' }, { k: 'limit', v: '100' }, { k: 'types', v: '' }], auth: true }
  ];

  /* ------------------------------------------------------ theme */
  SI.applyTheme = function () {
    var t = state.theme === 'dark' ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', t);
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', t === 'dark' ? '#241725' : '#FBEFF5');
  };
  SI.toggleTheme = function () {
    state.theme = state.theme === 'light' ? 'dark' : 'light';
    SI.save(); SI.applyTheme(); SI.emit('theme', state.theme);
  };
  SI.applyTheme();
})();

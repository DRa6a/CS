/* ============================================================
   点歌台 · 页面控制器
   body[data-page] 决定初始化哪个页面。
   原则：玩家说的话不是接口字段；报错要告诉用户「怎么办」，不是 HTTP 码。
   ============================================================ */
(function () {
  'use strict';
  var SI = window.SI;
  var $ = SI.qs, $$ = SI.qsa, esc = SI.esc;

  /* ------------------------------------------------------ 公共工具 */
  function setHTML(sel, html) { var el = $(sel); if (el) el.innerHTML = html; }
  function empty(title, sub, icon) {
    return '<div class="empty"><span class="big">' + (icon || '♫') + '</span>' + esc(title) +
      (sub ? '<p>' + esc(sub) + '</p>' : '') + '</div>';
  }
  function badge(text, cls) { return '<span class="badge ' + (cls || '') + '">' + esc(text) + '</span>'; }
  function busy(btn, on, label) {
    if (!btn) return;
    if (on) { btn.dataset.old = btn.innerHTML; btn.disabled = true; btn.innerHTML = label || '稍等…'; }
    else { btn.disabled = false; if (btn.dataset.old) btn.innerHTML = btn.dataset.old; }
  }
  function showMsg(sel, text, kind) {
    var el = $(sel); if (!el) return;
    el.innerHTML = text ? '<div class="msg ' + (kind || 'info') + '">' + esc(text) + '</div>' : '';
  }
  function bindChips(root, onPick) {
    if (!root) return;
    root.addEventListener('click', function (e) {
      var chip = e.target.closest('.chip');
      if (!chip || !root.contains(chip)) return;
      $$('.chip', root).forEach(function (c) { c.classList.remove('active'); });
      chip.classList.add('active');
      if (onPick) onPick(chip.dataset.value || '');
    });
  }
  function targetValue(sel) { var el = $(sel); return el ? el.value : (SI.state.target || 'self'); }

  /* 把接口/HTTP 的错误翻译成玩家听得懂、且知道下一步怎么办的话 */
  var ERROR_MAP = [
    [/发得太快|3000 -/, '刚发过一句，缓两秒再说～'],
    [/太快|冷却|429/, '刚点过一首，等几秒再点就好～'],
    [/无版权|NONE/, '这首暂时播不了，换一首试试'],
    [/不在线|离线/, '这位玩家现在不在服务器里'],
    [/不在服务器里/, '你当前不在服务器里，会改在这台设备上播放'],
    [/Cu 模组|没有安装 Cu/, '对方客户端还没装 Cu，会改在这台设备上播放'],
    [/绑定|MUSIC_U|我喜欢的音乐/, '先去「设置 → 网易云账号」绑定一下就能听'],
    [/音频地址|http\/https/, '这首歌的播放地址不对，换一首'],
    [/401|请先登录|口令|token/i, '连接过期了，去「设置」里重新接一下'],
    [/连不上|网络错误|Failed to fetch/, '连不上服务器，检查地址或切回试听模式'],
    [/响应不是 JSON/, '服务器返回的内容不对，确认填的是点歌台地址'],
    [/密码/, '密码不对，再检查一下']
  ];
  function friendly(e) {
    if (e && e.status === 0) return '连不上服务器，检查地址或切回试听模式';
    var m = (e && e.message) ? String(e.message) : '出了点小状况';
    for (var i = 0; i < ERROR_MAP.length; i++) if (ERROR_MAP[i][0].test(m)) return ERROR_MAP[i][1];
    return m;
  }
  SI.friendly = friendly;
  var OFFLINE_RE = /不在线|离线|不在服务器里|没有安装 Cu|Cu 模组/;

  /* ------------------------------------------------------ 歌曲组件（带专辑封面） */
  var SONG_HINT = {
    OK:      null,
    LOGIN:   { label: '会员 · 绑定后能听', cls: 'warn' },
    NONE:    { label: '暂时听不了',        cls: 'danger' },
    UNKNOWN: null
  };
  function songRow(s, opts) {
    opts = opts || {};
    var hint = SONG_HINT[s.state] || null;
    var off = s.state === 'NONE';
    return '<div class="song-row">' +
      SI.cover(s, 'sm') +
      '<div class="grow"><div class="title">' + esc(s.name) + '</div>' +
      '<div class="meta">' + esc(s.artist) + (s.album ? ' · ' + esc(s.album) : '') + '</div></div>' +
      '<span class="dur">' + SI.fmtDur(s.duration) + '</span>' +
      (hint ? '<span class="badge ' + hint.cls + '">' + hint.label + '</span>' : '') +
      '<button class="btn btn-request" data-song="' + esc(JSON.stringify(s)) + '"' +
        (off ? ' disabled data-perm="1"' : '') + ' title="点这首歌">' + SI.icon('plus') + '</button>' +
      '</div>';
  }
  function songCard(s) {
    var off = s.state === 'NONE';
    return '<div class="song-card">' + SI.cover(s, 'card-cover') +
      '<div class="name">' + esc(s.name) + '</div>' +
      '<div class="sub">' + esc(s.artist) + ' · ' + SI.fmtDur(s.duration) + '</div>' +
      '<button class="play-fab btn-request" data-song="' + esc(JSON.stringify(s)) + '"' +
        (off ? ' disabled data-perm="1"' : '') + ' title="点这首歌">' + SI.icon('play') + '</button>' +
      '</div>';
  }
  function bindRequestButtons(root, getTarget) {
    if (!root || root.dataset.bound) return;
    root.dataset.bound = '1';
    root.addEventListener('click', function (e) {
      var btn = e.target.closest('.btn-request');
      if (!btn || btn.disabled || !root.contains(btn)) return;
      var song;
      try { song = JSON.parse(btn.getAttribute('data-song')); } catch (err) { return; }
      busy(btn, true, '…');
      SI.requestSong(song, getTarget()).then(function () { busy(btn, false); });
    });
  }

  /* ------------------------------------------------------ 点歌 + 冷却 + 离线网页播放 */
  SI.cooldownUntil = 0;
  SI.startCooldown = function (sec) {
    SI.cooldownUntil = Math.max(SI.cooldownUntil, Date.now() + sec * 1000);
    $$('.btn-request').forEach(function (b) { if (b.disabled && !b.dataset.perm) b.dataset.perm = '1'; });
    (function tick() {
      var left = Math.max(0, Math.ceil((SI.cooldownUntil - Date.now()) / 1000));
      $$('.btn-request').forEach(function (b) {
        if (left > 0) b.disabled = true;
        else if (b.dataset.perm === '1') b.disabled = false;
      });
      if (left > 0) setTimeout(tick, 350);
    })();
  };
  SI.requestSong = async function (song, target) {
    if (Date.now() < SI.cooldownUntil) { SI.toast('刚点过一首，等几秒再点～', 'warn'); return false; }
    try {
      var body = { id: Number(song.id) || 0, name: song.name || '', artist: song.artist || '',
                url: song.url || '', target: target || 'self' };
      // 音源选择：server=跟随服务器默认；其它值覆盖服务器默认
      var src = SI.state.musicSource;
      if (src && src !== 'server') body.source = src;
      await SI.api('/api/request', { method: 'POST', body: body });
      var to = target === '__all__' ? '全服' : (target === 'self' ? '你自己' : target);
      SI.toast('《' + song.name + '》已经点上，' + (to === '你自己' ? '马上放给你听' : '放给 ' + to), 'ok');
      SI.startCooldown(SI.state.demo ? 2 : 8);
      SI.emit('song-requested', song);
      if (SI.loadSide) SI.loadSide();
      return true;
    } catch (e) {
      /* 目标玩家不在服内 / 自己不在线 / 没装 Cu：服务端会拒绝，
         这时不报错，直接由网页播放器在这台设备上播。 */
      if (OFFLINE_RE.test(e.message || '')) {
        SI.webPlay(song, '对方不在线 · 已在本机播放');
        SI.toast('对方不在服里，已经在这台设备上放给你听啦 ♪', 'ok');
        SI.startCooldown(SI.state.demo ? 2 : 8);
        SI.emit('song-requested', song);
        return true;
      }
      SI.toast(friendly(e), 'error');
      if (e.status === 429) SI.startCooldown(2);
      return false;
    }
  };

  /* ------------------------------------------------------ modal */
  function openModal(html) {
    var host = $('#modalHost'); if (!host) return;
    host.innerHTML = '<div class="modal-mask"><div class="modal">' + html + '</div></div>';
    SI.qsa('.modal-mask').forEach(function (mask) {
      mask.addEventListener('click', function (e) { if (e.target === mask) closeModal(); });
    });
    var x = $('.modal [data-close]'); if (x) x.addEventListener('click', closeModal);
  }
  function closeModal() { var h = $('#modalHost'); if (h) h.innerHTML = ''; }
  SI.openModal = openModal; SI.closeModal = closeModal;

  async function recommendSongs() {
    var queries = ['周杰伦', 'C418', '五月天', '邓紫棋'];
    var results = await Promise.all(queries.map(function (q) {
      return SI.api('/api/search', { params: { q: q } })
        .then(function (r) { return (r.data && r.data.songs) || []; })
        .catch(function () { return []; });
    }));
    var seen = {}, out = [];
    results.forEach(function (list) {
      list.forEach(function (s) { if (!seen[s.id] && s.state !== 'NONE') { seen[s.id] = 1; out.push(s); } });
    });
    SI.rememberCovers(out);
    return out.slice(0, 8);
  }

  /* ============================================================ 点歌（首页） */
  var playlistCache = [];
  function parsePlaylistId(text) {
    var m = String(text || '').match(/(\d{5,})/);
    return m ? Number(m[1]) : 0;
  }
  async function loadPlaylists(q) {
    var r = await SI.api('/api/playlists', { params: { q: q || '', limit: 20 } });
    playlistCache = (r.data && r.data.playlists) || [];
    setHTML('#playlistGrid', playlistCache.length ? playlistCache.map(function (p) {
      return '<div class="card playlist-card" data-playlist="' + esc(p.id) + '">' +
        SI.cover(p, 'card-cover') +
        '<div class="name">' + esc(p.name) + '</div>' +
        '<div class="desc">' + esc(p.description || (p.trackCount + ' 首 · ' + p.creator)) + '</div>' +
        '<div class="row" style="margin-top:10px;gap:6px">' + badge(p.trackCount + ' 首') +
        '<span class="small dim">' + SI.fmtNum(p.playCount) + ' 次播放</span></div>' +
        '</div>';
    }).join('') : empty('没找到歌单', '换个词，或直接粘歌单链接', '♫'));
  }
  async function openPlaylist(id) {
    var r = await SI.api('/api/playlist', { params: { id: id, limit: 100 } });
    var pl = r.data.playlist || {}, songs = r.data.songs || [];
    SI.rememberCovers(songs);
    openModal(
      '<button class="icon-btn close" data-close style="float:right">✕</button>' +
      '<div class="row" style="gap:14px;align-items:flex-start">' +
        '<div style="width:88px;flex:none">' + SI.cover(pl, 'card-cover') + '</div>' +
        '<div class="grow"><h3>' + esc(pl.name || '歌单') + '</h3>' +
        '<p class="card-sub">' + esc(pl.description || '') + '</p>' +
        '<div class="row wrap" style="margin-top:10px;gap:6px">' + badge(songs.length + ' 首') +
        '<span class="small dim">by ' + esc(pl.creator || '—') + '</span></div></div>' +
      '</div>' +
      '<div class="row" style="gap:8px;margin:16px 0 12px">' +
        '<button class="btn btn-primary btn-sm" id="btnBatch">连点前 3 首</button>' +
        '<button class="btn btn-sm btn-ghost" data-close>先看看</button></div>' +
      '<div class="list-scroll" id="modalSongs">' + songs.map(function (s) { return songRow(s); }).join('') + '</div>'
    );
    var host = $('#modalSongs');
    bindRequestButtons(host, function () { return targetValue('#requestTarget'); });
    var batch = $('#btnBatch');
    if (batch) batch.addEventListener('click', async function () {
      var picks = songs.filter(function (s) { return s.state !== 'NONE'; }).slice(0, 3);
      busy(batch, true, '正在点…');
      for (var i = 0; i < picks.length; i++) {
        await SI.requestSong(picks[i], targetValue('#requestTarget'));
        if (i < picks.length - 1) await SI.sleep(SI.state.demo ? 2300 : 9000);
      }
      busy(batch, false);
      SI.toast('前 ' + picks.length + ' 首都点上啦', 'ok');
    });
  }

  async function initRequest() {
    var input = $('#searchInput'), results = $('#searchResults');
    SI.fillTargets($('#requestTarget'));
    bindRequestButtons(results, function () { return targetValue('#requestTarget'); });

    /* 音源开关：只决定这次点歌请求带哪个 source，不写入、也不改服务端配置 */
    var srcSel = $('#requestSource');
    if (srcSel) {
      srcSel.value = SI.state.musicSource || 'server';
      srcSel.addEventListener('change', function () {
        SI.set({ musicSource: this.value });
        if (this.value !== 'server') {
          SI.toast('点歌音源：' + this.options[this.selectedIndex].text, 'ok');
        } else {
          SI.toast('点歌音源：跟随服务器默认', 'ok');
        }
      });
    }

    /* 内置网页的「用直链点歌」：音频地址 + 可选歌名 */
    var btnRaw = $('#btnRaw');
    if (btnRaw) btnRaw.addEventListener('click', async function () {
      var url = $('#rawUrl').value.trim(), name = $('#rawName').value.trim();
      if (!url) { SI.toast('先填音频地址', 'warn'); return; }
      busy(btnRaw, true, '点歌中…');
      await SI.requestSong({ id: 0, name: name || '自定义音频', artist: '直链', url: url, state: 'OK' },
        targetValue('#requestTarget'));
      busy(btnRaw, false);
    });

    /* 清空搜索 + 网易云未绑定提示（对齐内置网页） */
    var btnClear = $('#clearSearch');
    if (btnClear) btnClear.addEventListener('click', function () {
      input.value = '';
      results.innerHTML = empty('还没有搜索', '试试「周杰伦」「晴天」或者「C418」');
      setHTML('#searchMeta', '输入关键词开始找歌');
      setHTML('#resultHint', '');
    });
    SI.api('/api/netease/status').then(function (r) {
      var ne = (r.data && r.data.netease) || {};
      var tip = $('#neteaseTip');
      if (tip && !ne.bound) tip.classList.remove('hidden');
    }).catch(function () {});

    async function doSearch(q) {
      q = String(q || '').trim();
      if (!q) { results.innerHTML = empty('还没有搜索', '试试「周杰伦」「晴天」或者「C418」'); return; }
      results.innerHTML = '<div class="empty"><span class="big">♫</span>正在找…</div>';
      try {
        var r = await SI.api('/api/search', { params: { q: q } });
        var songs = (r.data && r.data.songs) || [];
        SI.rememberCovers(songs);
        setHTML('#searchMeta', '找到 ' + songs.length + ' 首');
        setHTML('#resultHint', songs.length ? '点右侧的 + 就能加进队列' : '');
        results.innerHTML = songs.length ? songs.map(function (s) { return songRow(s); }).join('')
          : empty('没找到「' + q + '」', '换个歌名、歌手，或直接用歌曲号', '🔍');
      } catch (e) {
        results.innerHTML = empty(friendly(e), '', '!');
        setHTML('#searchMeta', '没搜成');
      }
    }
    $('#btnSearch').addEventListener('click', function () { doSearch(input.value); });
    input.addEventListener('keydown', function (e) { if (e.key === 'Enter') doSearch(input.value); });
    input.addEventListener('input', SI.debounce(function () { if (input.value.trim()) doSearch(input.value); }, 480));
    var fromUrl = SI.param('q');
    if (fromUrl) { input.value = fromUrl; doSearch(fromUrl); }

    /* 我喜欢的音乐（网易云收藏） */
    $('#btnLiked').addEventListener('click', async function () {
      var btn = this; busy(btn, true, '读取中…');
      try {
        var r = await SI.api('/api/liked', { params: { limit: 100, offset: 0 } });
        var songs = (r.data && r.data.songs) || [];
        SI.rememberCovers(songs);
        setHTML('#likedResults', songs.length ? songs.map(function (s) { return songRow(s); }).join('')
          : empty('收藏里还没有歌', '', '♥'));
        bindRequestButtons($('#likedResults'), function () { return targetValue('#requestTarget'); });
        setHTML('#likedMeta', '共 ' + songs.length + ' 首 · 点 + 可以直接点出去');
      } catch (e) {
        setHTML('#likedResults', empty(friendly(e), '设置里绑定网易云账号后就能读到', '🔑'));
        setHTML('#likedMeta', '');
      }
      busy(btn, false);
    });

    /* 打开歌单：卡片点开，或粘贴歌单链接 / id 直接打开 */
    $('#playlistGrid').addEventListener('click', function (e) {
      var card = e.target.closest('[data-playlist]');
      if (!card) return;
      openPlaylist(card.dataset.playlist).catch(function (err) { SI.toast(friendly(err), 'error'); });
    });
    $('#playlistSearch').addEventListener('input', SI.debounce(function () {
      loadPlaylists(this.value.trim()).catch(function (e) { SI.toast(friendly(e), 'error'); });
    }, 380));
    function openById() {
      var id = parsePlaylistId($('#playlistId').value);
      if (!id) { SI.toast('把歌单链接或歌单号粘进来', 'warn'); return; }
      openPlaylist(id).catch(function (e) { SI.toast(friendly(e), 'error'); });
    }
    $('#btnOpenPlaylist').addEventListener('click', openById);
    $('#playlistId').addEventListener('keydown', function (e) { if (e.key === 'Enter') openById(); });
    loadPlaylists('').catch(function (e) { setHTML('#playlistGrid', empty(friendly(e), '', '!')); });

    try {
      var list = await recommendSongs();
      setHTML('#recommendGrid', list.length ? list.map(songCard).join('') : empty('暂时没有推荐', '', '♫'));
      bindRequestButtons($('#recommendGrid'), function () { return targetValue('#requestTarget'); });
    } catch (e) { setHTML('#recommendGrid', empty('推荐加载失败', '', '!')); }
  }

  /* ============================================================ 聊天（喊话 + 服务器动态） */
  var QUICK_LINES = ['晚上八点打龙，别迟到', '有人在挖矿吗', '这首送给大家', '主城集合', '谢谢点歌～'];
  async function loadChat() {
    var r = await SI.api('/api/chat');
    var msgs = (r.data && r.data.messages) || [];
    var list = msgs.slice().reverse();
    setHTML('#chatList', list.length ? list.map(function (m) {
      return '<div class="chat-msg ' + (m.mine ? 'mine' : '') + '">' +
        SI.avatar(m.name, m.avatar) +
        '<div class="body"><div class="who">' + esc(m.name) + ' · ' + SI.ago(m.at) + '</div>' +
        '<div class="bubble">' + esc(m.text) + '</div></div></div>';
    }).join('') : empty('还没有人说话', '发第一句打个招呼吧', '💬'));
    var box = $('#chatList'); if (box) box.scrollTop = box.scrollHeight;
  }

  var EVENT_LABEL = { chat: '聊天', web: '网页喊话', join: '进服', quit: '离服', death: '死亡', command: '命令' };
  var eventBuf = [], eventSeq = 0, eventType = 'all';
  function renderEvents() {
    var list = eventBuf.slice().reverse();
    setHTML('#eventCount', '共 ' + list.length + ' 条');
    setHTML('#eventList', list.length ? list.map(function (e) {
      var label = EVENT_LABEL[e.type] || e.type;
      var cls = e.type === 'death' ? 'danger' : (e.type === 'join' ? 'ok' : (e.type === 'web' ? 'info' : ''));
      return '<div class="tl-item"><span class="tl-time">' + esc(e.time || SI.fmtTime(e.at)) + '</span>' +
        '<div class="tl-body"><div class="who">' + esc(e.player || '系统') + ' ' + badge(label, cls) + '</div>' +
        '<div class="what">' + esc(e.text || '') + (e.detail ? ' · ' + esc(e.detail) : '') + '</div></div></div>';
    }).join('') : empty('还没有动态', '', '◷'));
  }
  async function loadEvents(incremental) {
    var types = eventType === 'all' ? '' : eventType;
    var r = await SI.api('/api/events', { params: { since: incremental ? eventSeq : 0, limit: 200, types: types } });
    var d = r.data || {};
    eventSeq = d.latest || eventSeq;
    var incoming = d.events || [];
    eventBuf = incremental ? incoming.concat(eventBuf) : incoming.slice();
    if (eventBuf.length > 200) eventBuf.length = 200;
    setHTML('#recordedTypes', (d.recorded || []).map(function (t) { return EVENT_LABEL[t] || t; }).join(' · ') || '—');
    renderEvents();
  }

  async function initChat() {
    /* 喊话与服务器动态合并成一页，用顶部视图切换 */
    var tabs = $('#viewTabs');
    if (tabs) tabs.addEventListener('click', function (e) {
      var b = e.target.closest('[data-view]'); if (!b) return;
      $$('[data-view]', tabs).forEach(function (x) { x.classList.remove('active'); });
      b.classList.add('active');
      var isChat = b.dataset.view === 'chat';
      $('#chatView').classList.toggle('hidden', !isChat);
      $('#eventsView').classList.toggle('hidden', isChat);
    });

    var input = $('#chatInput');
    if (input) input.addEventListener('input', function () {
      setHTML('#msgCount', this.value.length + ' / 120');
    });
    SI.fillTargets($('#chatTarget'), 'chat');
    setHTML('#quickLines', QUICK_LINES.map(function (t) {
      return '<button class="quick-line">' + esc(t) + '</button>';
    }).join(''));
    $('#quickLines').addEventListener('click', function (e) {
      var b = e.target.closest('.quick-line'); if (!b) return;
      input.value = b.textContent; input.focus();
    });
    async function send() {
      var text = input.value.trim();
      if (!text) { SI.toast('先写点什么吧', 'warn'); return; }
      var btn = $('#btnSend'); busy(btn, true, '发送中…');
      try {
        await SI.api('/api/message', { method: 'POST', body: { target: targetValue('#chatTarget'), text: text } });
        input.value = '';
        SI.toast('已经发到游戏里了', 'ok');
        await loadChat();
      } catch (e) { SI.toast(friendly(e), 'error'); }
      busy(btn, false);
    }
    $('#btnSend').addEventListener('click', send);
    input.addEventListener('keydown', function (e) { if (e.key === 'Enter') send(); });
    $('#btnRefreshChat').addEventListener('click', function () { loadChat().catch(function (e) { SI.toast(friendly(e), 'error'); }); });
    try {
      var r = await SI.api('/api/me');
      setHTML('#onlineHint', ((r.data && r.data.server && r.data.server.online) || 0) + ' 人');
    } catch (e) { setHTML('#onlineHint', '—'); }
    try { await loadChat(); } catch (e) { setHTML('#chatList', empty(friendly(e), '', '!')); }

    /* 服务器动态 */
    bindChips($('#eventTypes'), function (v) {
      eventType = v || 'all'; eventSeq = 0; eventBuf = [];
      loadEvents(false).catch(function (e) { SI.toast(friendly(e), 'error'); });
    });
    $('#btnRefreshEvents').addEventListener('click', function () {
      loadEvents(false).then(function () { SI.toast('刷新好了', 'ok'); }).catch(function (e) { SI.toast(friendly(e), 'error'); });
    });
    $('#btnExportEvents').addEventListener('click', function () {
      SI.download('si-events.json', JSON.stringify(eventBuf, null, 2));
      SI.toast('已经导出', 'ok');
    });
    $('#eventAuto').addEventListener('change', function () {
      if (this.checked) SI.poll('events', function () { return loadEvents(true); }, 6000);
      else SI.stopPoll('events');
    });
    try { await loadEvents(false); } catch (e) { setHTML('#eventList', empty(friendly(e), '', '!')); }

    SI.poll('chat', function () { return loadChat(); }, 6000);
    SI.poll('events', function () { return loadEvents(true); }, 6000);
  }

  /* ============================================================ 设置 */
  async function refreshConn() {
    var badgeEl = $('#connState');
    try {
      var r = await SI.api('/api/me');
      var d = r.data || {}, s = d.server || {}, u = d.user || {};
      setHTML('#myAvatar', SI.avatar(u.name || SI.state.username, u.avatar, 'lg'));
      setHTML('#myName', esc(u.name || SI.state.username || '玩家'));
      setHTML('#myServer', esc(s.name || (SI.state.demo ? '试听服务器' : '未命名服务器')));
      setHTML('#onlineCount', esc(s.online || 0));
      if (badgeEl) badgeEl.className = 'badge ' + (SI.state.demo ? 'info' : 'ok');
      if (badgeEl) badgeEl.textContent = SI.state.demo ? '试听模式' : '已连上';
      setHTML('#connExplain', SI.state.demo
        ? '现在是试听模式：所有操作只在这台电脑上生效，随便点、随便试都不会影响真实服务器。服主发一个带链接的地址，点开就会自动连上。'
        : '已经连到「' + esc(s.name || '服务器') + '」。点的歌会真的进游戏，喊的话也会出现在聊天栏里。');
    } catch (e) {
      if (badgeEl) { badgeEl.className = 'badge danger'; badgeEl.textContent = '没连上'; }
      setHTML('#myServer', friendly(e));
      setHTML('#connExplain', '连不上真实服务器，可以在下面切回试听模式先逛逛。');
    }
    setHTML('#versionText', SI.version + ' / Si API v1');
    setHTML('#sourceText', SI.state.demo ? '本机试听数据' : SI.apiBase());
    var st = $('#neteaseState');
    if (st) {
      try {
        var n = await SI.api('/api/netease/status');
        var ne = (n.data && n.data.netease) || {};
        st.textContent = ne.bound ? ('已绑定 · ' + (ne.nickname || '网易云账号')) : '还没绑定';
      } catch (err) { st.textContent = '暂时读不到'; }
    }
  }
  async function initSettings() {
    $$('[data-netease]').forEach(function (chip) {
      chip.addEventListener('click', function () {
        $$('[data-netease]').forEach(function (c) { c.classList.remove('active'); });
        chip.classList.add('active');
        ['qr', 'phone', 'cookie'].forEach(function (k) {
          var pane = $('#pane-' + k); if (pane) pane.classList.toggle('hidden', k !== chip.dataset.netease);
        });
        showMsg('#neteaseResult', '');
      });
    });

    var theme = $('#themeToggle');
    theme.checked = SI.state.theme === 'light';
    theme.addEventListener('change', function () {
      if ((SI.state.theme === 'light') !== this.checked) SI.toggleTheme();
      SI.toast(this.checked ? '换回樱花日啦' : '换成樱花夜啦', 'ok');
    });
    SI.fillTargets($('#defaultTarget'));
    var offline = $('#simulateOffline');
    if (offline) {
      offline.checked = !!SI.state.simulateOffline;
      offline.addEventListener('change', function () {
        SI.set({ simulateOffline: this.checked });
        SI.toast(this.checked ? '已开启：点歌会走「目标不在线」分支' : '已关闭', 'ok');
      });
    }

    /* 点歌音源（覆盖服务器默认）：跟随服务器 / 网易云 / 适配服务 / 自动 */
    var srcSel = $('#musicSource');
    if (srcSel) {
      srcSel.value = SI.state.musicSource || 'server';
      srcSel.addEventListener('change', function () {
        SI.set({ musicSource: this.value });
        SI.toast(this.value === 'server' ? '已改为跟随服务器默认音源'
          : ('点歌音源已设为：' + this.options[this.selectedIndex].text), 'ok');
      });
    }
    $('#tokenInput').value = SI.state.token && !SI.state.demo ? SI.state.token : '';

    /* 退出登录（内置网页有） */
    var btnLogout = $('#btnLogout');
    if (btnLogout) btnLogout.addEventListener('click', async function () {
      try { await SI.api('/api/logout', { method: 'POST' }); } catch (e) {}
      SI.set({ token: '', demo: true });
      SI.toast('已退出，回到试听模式', 'ok');
      setTimeout(function () { location.href = 'index.html'; }, 400);
    });

    $('#apiUrl').value = SI.state.baseUrl || 'http://127.0.0.1:8765';
    $('#loginName').value = SI.state.username || '';
    $('#btnConnect').addEventListener('click', async function () {
      var btn = this, url = $('#apiUrl').value.trim().replace(/\/+$/, '');
      var name = $('#loginName').value.trim(), pwd = $('#loginPwd').value;
      if (!url || !name || !pwd) { showMsg('#connectResult', '把地址、名字和密码都填一下', 'error'); return; }
      busy(btn, true, '连接中…');
      var wasDemo = SI.state.demo, oldUrl = SI.state.baseUrl;
      SI.set({ baseUrl: url, demo: false, token: '' });
      try {
        await SI.api('/api/health', { auth: false });
        var r = await SI.api('/api/login', { method: 'POST', auth: false, body: { username: name, password: pwd } });
        SI.set({ token: r.data.token || '', username: name, demo: false });
        showMsg('#connectResult', '连上了！马上带你回点歌台', 'ok');
        setTimeout(function () { location.href = 'index.html'; }, 700);
      } catch (e) {
        SI.set({ demo: wasDemo, baseUrl: oldUrl, token: wasDemo ? 'demo_session' : '' });
        showMsg('#connectResult', friendly(e) + '，已回到试听模式', 'error');
        busy(btn, false);
      }
    });
    $('#btnBackDemo').addEventListener('click', function () {
      SI.set({ demo: true, token: 'demo_session' });
      SI.toast('回到试听模式啦', 'ok');
      setTimeout(function () { location.reload(); }, 400);
    });

    $('#btnQr').addEventListener('click', async function () {
      var btn = this; busy(btn, true, '生成中…');
      try {
        var r = await SI.api('/api/netease/qr', { method: 'POST' });
        $('#qrImg').src = r.data.image;
        $('#qrBox').classList.remove('hidden');
        setHTML('#qrStatus', '用网易云 App 扫一下');
        var key = r.data.key;
        SI.poll('qr', async function () {
          var p = await SI.api('/api/netease/qr/poll', { params: { key: key } });
          var s = p.data.status;
          if (s === 'SCANNED') setHTML('#qrStatus', '扫到了，去手机上点一下确认');
          if (s === 'CONFIRMED') {
            SI.stopPoll('qr');
            setHTML('#qrStatus', '绑定好啦');
            SI.toast('网易云账号绑定成功', 'ok');
            refreshConn();
          }
        }, 1600);
      } catch (e) { showMsg('#neteaseResult', friendly(e), 'error'); }
      busy(btn, false);
    });
    $('#btnPhone').addEventListener('click', async function () {
      var btn = this; busy(btn, true, '绑定中…');
      try {
        await SI.api('/api/netease/login', { method: 'POST', body: { phone: $('#phoneNum').value.trim(), password: $('#phonePwd').value } });
        showMsg('#neteaseResult', '绑定成功，会员歌可以听了', 'ok'); refreshConn();
      } catch (e) { showMsg('#neteaseResult', friendly(e), 'error'); }
      busy(btn, false);
    });
    $('#btnCookie').addEventListener('click', async function () {
      var btn = this; busy(btn, true, '绑定中…');
      try {
        await SI.api('/api/netease/cookie', { method: 'POST', body: { musicU: $('#musicU').value.trim() } });
        showMsg('#neteaseResult', '绑定成功', 'ok'); refreshConn();
      } catch (e) { showMsg('#neteaseResult', friendly(e), 'error'); }
      busy(btn, false);
    });
    $('#btnUnbind').addEventListener('click', async function () {
      try {
        await SI.api('/api/netease/logout', { method: 'POST' });
        SI.toast('已经解除绑定', 'ok'); refreshConn();
      } catch (e) { SI.toast(friendly(e), 'error'); }
    });
    $('#btnPassword').addEventListener('click', async function () {
      var btn = this; busy(btn, true, '保存中…');
      try {
        await SI.api('/api/password', { method: 'POST', body: { oldPassword: $('#oldPwd').value, newPassword: $('#newPwd').value } });
        showMsg('#passwordResult', '密码改好了，下次登录用新密码', 'ok');
      } catch (e) { showMsg('#passwordResult', friendly(e), 'error'); }
      busy(btn, false);
    });
    $('#btnToken').addEventListener('click', function () {
      SI.set({ token: $('#tokenInput').value.trim() });
      showMsg('#tokenResult', '口令已保存', 'ok');
    });
    $('#btnReseed').addEventListener('click', function () {
      if (SI.mock && SI.mock.reseed) SI.mock.reseed();
      SI.toast('试听数据已经重置', 'ok');
      setTimeout(function () { location.reload(); }, 400);
    });

    refreshConn();
  }

  /* ============================================================ 接口调试台（高级） */
  var currentEp = null;
  function renderEpList() {
    setHTML('#epCount', SI.ENDPOINTS.length + ' 个');
    setHTML('#epList', SI.ENDPOINTS.map(function (ep, i) {
      return '<div class="ep-item ' + (currentEp === ep ? 'active' : '') + '" data-ep="' + i + '">' +
        '<span class="ep-method">' + ep.method + '</span><span class="p">' + esc(ep.path) + '</span></div>';
    }).join(''));
  }
  function selectEp(i) {
    currentEp = SI.ENDPOINTS[i];
    renderEpList();
    setHTML('#epTitle', esc(currentEp.desc));
    setHTML('#epMethod', esc(currentEp.method));
    setHTML('#epDesc', esc(currentEp.path) + (currentEp.auth ? ' · 需要连接' : ' · 无需连接'));
    var ps = currentEp.params || [];
    setHTML('#epParams', ps.length
      ? '<label class="small muted" style="font-weight:700">地址参数</label>' + ps.map(function (p) {
          return '<div class="field"><label>' + esc(p.k) + '</label><input class="input mono" data-param="' +
            esc(p.k) + '" value="' + esc(p.v) + '"></div>';
        }).join('')
      : '<p class="small dim">这个接口不用填参数</p>');
    $('#epBody').value = currentEp.body ? JSON.stringify(currentEp.body, null, 2) : '';
    setHTML('#apiMeta', '—');
    setHTML('#apiResult', '等待请求…');
  }
  function renderLog() {
    var rows = SI.log.slice(0, 12);
    setHTML('#logList', rows.length ? rows.map(function (l) {
      return '<div class="row" style="padding:8px 2px;border-bottom:1px solid var(--line-soft);font-size:11.5px">' +
        badge(String(l.status), l.status < 400 ? 'ok' : 'danger') +
        '<span class="mono grow" style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' +
        esc(l.method) + ' ' + esc(l.url) + '</span>' +
        '<span class="dim mono">' + esc(String(l.ms)) + 'ms</span></div>';
    }).join('') : empty('还没有请求', '', '◷'));
  }
  function curlCommand() {
    if (!currentEp) return '';
    var base = SI.state.demo ? 'http://127.0.0.1:8765' : SI.apiBase();
    var usp = new URLSearchParams();
    $$('#epParams [data-param]').forEach(function (i) { if (i.value !== '') usp.set(i.dataset.param, i.value); });
    var qs = usp.toString();
    var cmd = 'curl -X ' + currentEp.method + ' "' + base + currentEp.path + (qs ? '?' + qs : '') + '"';
    if (currentEp.auth && SI.state.token && !SI.state.demo) cmd += ' \\\n  -H "Authorization: Bearer ' + SI.state.token + '"';
    var raw = $('#epBody').value.trim();
    if (raw) { try { cmd += ' \\\n  -H "Content-Type: application/json" -d \'' + JSON.stringify(JSON.parse(raw)) + '\''; } catch (e) {} }
    return cmd;
  }
  async function initApi() {
    renderEpList(); renderLog();
    selectEp(0);
    $('#epList').addEventListener('click', function (e) {
      var it = e.target.closest('[data-ep]'); if (it) selectEp(Number(it.dataset.ep));
    });
    $('#btnSendApi').addEventListener('click', async function () {
      if (!currentEp) return;
      var btn = this, params = {}, body;
      $$('#epParams [data-param]').forEach(function (i) { if (i.value !== '') params[i.dataset.param] = i.value; });
      var raw = $('#epBody').value.trim();
      if (raw) { try { body = JSON.parse(raw); } catch (e) { SI.toast('请求内容不是合法的 JSON', 'error'); return; } }
      busy(btn, true, '请求中…');
      try {
        var r = await SI.api(currentEp.path, { method: currentEp.method, params: params, body: body, auth: currentEp.auth });
        setHTML('#apiMeta', 'HTTP ' + r.status + ' · ' + r.ms + 'ms' + (r.notModified ? ' · 内容没变化' : ''));
        setHTML('#apiResult', esc(JSON.stringify(r.data, null, 2)));
      } catch (e) {
        setHTML('#apiMeta', 'HTTP ' + (e.status || 0) + ' · ' + friendly(e));
        setHTML('#apiResult', esc(JSON.stringify(e.data || { error: e.message }, null, 2)));
      }
      busy(btn, false); renderLog();
    });
    $('#btnCurl').addEventListener('click', function () { SI.copy(curlCommand()); });
    $('#btnCopyResp').addEventListener('click', function () { SI.copy($('#apiResult').textContent); });
    $('#btnClearLog').addEventListener('click', function () { SI.log.length = 0; renderLog(); });
    SI.on('request', renderLog);
  }

  /* ============================================================ 分发 */
  var PAGES = {
    request: initRequest, chat: initChat,
    settings: initSettings, api: initApi
  };
  function boot() {
    var page = document.body.dataset.page;
    var fn = PAGES[page];
    if (!fn) return;
    Promise.resolve().then(fn).catch(function (e) {
      SI.toast(friendly(e), 'error');
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();

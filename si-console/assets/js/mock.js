/* ============================================================
   Si Console · 演示数据层
   没有运行中的 Si 服务端时，让每个页面依然能完整操作。
   数据结构与 WebServer.java 的响应保持字段级一致，并持久化到 localStorage，
   因此跨页面（点歌 → 记录 / 发消息 → 对话 / 事件流）数据是连贯的。
   ============================================================ */
(function () {
  'use strict';
  var SI = window.SI;
  var DB_KEY = 'si_console_demo_db_v1';

  function seed() {
    var t = Date.now();
    var songs = [
      { id: 185737, name: '七里香', artist: '周杰伦', album: '七里香', duration: 298000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=185737.mp3' },
      { id: 186016, name: '晴天', artist: '周杰伦', album: '叶惠美', duration: 269000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=186016.mp3' },
      { id: 257062, name: '夜曲', artist: '周杰伦', album: '十一月的萧邦', duration: 226000, cover: '', fee: 8, state: 'LOGIN', url: 'https://music.163.com/song/media/outer/url?id=257062.mp3' },
      { id: 338943, name: '泡沫', artist: '邓紫棋', album: 'Xposed', duration: 256000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=338943.mp3' },
      { id: 133115, name: '光年之外', artist: '邓紫棋', album: '光年之外', duration: 235000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=133115.mp3' },
      { id: 409157, name: '富士山下', artist: '陈奕迅', album: 'What’s Going On…?', duration: 259000, cover: '', fee: 8, state: 'LOGIN', url: 'https://music.163.com/song/media/outer/url?id=409157.mp3' },
      { id: 64685,  name: '浮夸', artist: '陈奕迅', album: 'U-87', duration: 283000, cover: '', fee: 0, state: 'NONE', url: 'https://music.163.com/song/media/outer/url?id=64685.mp3' },
      { id: 385761, name: '倔强', artist: '五月天', album: '神的孩子都在跳舞', duration: 244000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=385761.mp3' },
      { id: 386721, name: '突然好想你', artist: '五月天', album: '后青春期的诗', duration: 293000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=386721.mp3' },
      { id: 452116, name: 'Mine Island', artist: 'C418', album: 'Minecraft Volume Alpha', duration: 194000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=452116.mp3' },
      { id: 452120, name: 'Sweden', artist: 'C418', album: 'Minecraft Volume Alpha', duration: 214000, cover: '', fee: 0, state: 'OK', url: 'https://music.163.com/song/media/outer/url?id=452120.mp3' },
      { id: 531051, name: 'Numb', artist: 'Linkin Park', album: 'Meteora', duration: 187000, cover: '', fee: 8, state: 'LOGIN', url: 'https://music.163.com/song/media/outer/url?id=531051.mp3' }
    ];

    var entries = [
      { id: 'a1b2c3d4e5', player: 'Alex', target: 'DRa6a', title: '七里香', artist: '周杰伦', state: 'PLAYING', message: '', playing: 1, listeners: 1, broadcast: false, requestedAt: t - 1000 * 46, mine: false },
      { id: 'b2c3d4e5f6', player: 'Steve', target: '', title: 'Mine Island', artist: 'C418', state: 'QUEUED', message: '', playing: 0, listeners: 6, broadcast: true, requestedAt: t - 1000 * 128, mine: false },
      { id: 'c3d4e5f6a7', player: 'DRa6a', target: 'Xiaomi', title: '泡沫', artist: '邓紫棋', state: 'DONE', message: '', playing: 0, listeners: 1, broadcast: false, requestedAt: t - 1000 * 420, mine: true },
      { id: 'd4e5f6a7b8', player: 'Kaze', target: 'Alex', title: '晴天', artist: '周杰伦', state: 'SKIPPED', message: '', playing: 0, listeners: 1, broadcast: false, requestedAt: t - 1000 * 860, mine: false },
      { id: 'e5f6a7b8c9', player: 'Xiaomi', target: 'Xiaomi', title: '光年之外', artist: '邓紫棋', state: 'OFFLINE', message: '目标玩家已离线', playing: 0, listeners: 1, broadcast: false, requestedAt: t - 1000 * 1500, mine: false },
      { id: 'f6a7b8c9d0', player: 'DRa6a', target: 'Steve', title: '倔强', artist: '五月天', state: 'DONE', message: '', playing: 0, listeners: 1, broadcast: false, requestedAt: t - 1000 * 2400, mine: true },
      { id: 'a7b8c9d0e1', player: 'Alex', target: 'Alex', title: '浮夸', artist: '陈奕迅', state: 'FAILED', message: '音频直链不可用（403）', playing: 0, listeners: 1, broadcast: false, requestedAt: t - 1000 * 3300, mine: false }
    ];

    var chatLines = [
      { id: 12, name: 'Alex', text: '有人去下界要塞吗，我缺烈焰棒', source: 'game', at: t - 1000 * 38 },
      { id: 11, name: 'DRa6a', text: '这首七里香送给大家 🎵', source: 'web', at: t - 1000 * 52 },
      { id: 10, name: 'Steve', text: '我这边刚挖到 12 颗钻石', source: 'game', at: t - 1000 * 96 },
      { id: 9,  name: 'Xiaomi', text: '服务器好卡，是不是有人在刷怪塔', source: 'game', at: t - 1000 * 180 },
      { id: 8,  name: 'DRa6a', text: '晚上八点集体打末影龙，别迟到', source: 'web', at: t - 1000 * 300 },
      { id: 7,  name: 'Kaze', text: '收到！我先把装备修一下', source: 'game', at: t - 1000 * 360 },
      { id: 6,  name: 'Alex', text: '谁把我的箱子挪位置了 😂', source: 'game', at: t - 1000 * 520 },
      { id: 5,  name: 'Steve', text: '主城门口的灯我补上了', source: 'game', at: t - 1000 * 700 }
    ];

    var rawEvents = [
      ['quit', 'Notch_UwU', '离开了服务器', '', 12],
      ['web', 'DRa6a', '这首七里香送给大家 🎵', '', 52],
      ['chat', 'Alex', '有人去下界要塞吗，我缺烈焰棒', '', 38],
      ['death', 'Steve', '被 苦力怕 击杀', 'mob', 145],
      ['command', 'DRa6a', '/gamemode spectator', 'player', 220],
      ['join', 'Xiaomi', '加入了服务器', '', 330],
      ['chat', 'Steve', '我这边刚挖到 12 颗钻石', '', 96],
      ['death', 'Alex', '掉进了虚空', 'void', 610],
      ['command', 'Kaze', '/tp DRa6a', 'player', 760],
      ['join', 'Kaze', '加入了服务器', '', 880],
      ['chat', 'Xiaomi', '服务器好卡，是不是有人在刷怪塔', '', 180],
      ['quit', 'Steve', '离开了服务器', '', 1050],
      ['web', 'DRa6a', '晚上八点集体打末影龙，别迟到', '', 300],
      ['chat', 'Kaze', '收到！我先把装备修一下', '', 360]
    ];
    // 按时间从旧到新编号，保证 since 增量语义成立
    rawEvents.sort(function (a, b) { return b[4] - a[4]; });
    var events = rawEvents.map(function (e, i) {
      var at = t - 1000 * e[4];
      return { id: i + 1, at: at, time: SI.fmtTime(at), type: e[0], player: e[1], text: e[2], detail: e[3] };
    });

    return {
      version: 1,
      songs: songs,
      playlists: [
        { id: 3778678, name: '华语经典 · 那些年的单曲循环', cover: '', trackCount: 128, creator: '夜航电台', playCount: 2841000, description: '从磁带到流媒体，总有一首能把你拉回某个下午。' },
        { id: 2884035, name: 'Minecraft 原声 | 挖矿专用', cover: '', trackCount: 42, creator: 'C418 爱好者', playCount: 986000, description: '安静、不抢戏，适合长时间建造与挖矿。' },
        { id: 5043210, name: '周杰伦 · 精选 30 首', cover: '', trackCount: 30, creator: 'JayList', playCount: 5620000, description: '按专辑顺序排好了，直接批量点给全服。' },
        { id: 6612904, name: '深夜写代码不犯困', cover: '', trackCount: 66, creator: 'LoopLab', playCount: 431000, description: '低干扰器乐，节奏稳定，不会被歌词打断思路。' }
      ],
      players: [
        { name: 'DRa6a', hasCu: true, self: true },
        { name: 'Alex', hasCu: true, self: false },
        { name: 'Steve', hasCu: true, self: false },
        { name: 'Xiaomi', hasCu: false, self: false },
        { name: 'Kaze', hasCu: true, self: false },
        { name: 'Notch_UwU', hasCu: false, self: false }
      ],
      entries: entries,
      messages: chatLines,
      events: events,
      eventSeq: events.length,
      chatSeq: 12,
      netease: { enabled: true, bound: true, nickname: '夜航电台' },
      lastRequestAt: 0,
      lastMessageAt: 0,
      qrPollCount: 0
    };
  }

  function loadDB() {
    try {
      var raw = localStorage.getItem(DB_KEY);
      if (raw) {
        var db = JSON.parse(raw);
        if (db && db.version === 1 && db.songs && db.entries) return db;
      }
    } catch (e) {}
    var fresh = seed();
    saveDB(fresh);
    return fresh;
  }
  function saveDB(db) {
    try { localStorage.setItem(DB_KEY, JSON.stringify(db)); } catch (e) {}
  }
  var db = loadDB();

  function server() {
    return {
      name: '星屿生存服',
      url: SI.state.demo ? 'http://mc.example.com:8765' : SI.apiBase(),
      online: db.players.length,
      allowBroadcast: true
    };
  }
  function user() {
    return {
      name: SI.state.username || 'DRa6a', online: true, hasCu: true,
      avatar: '', createdAt: Date.now() - 1000 * 60 * 60 * 24 * 92
    };
  }
  function me() {
    return { ok: true, loggedIn: true, user: user(), netease: Object.assign({}, db.netease), server: server() };
  }
  function needAuth(opts) {
    return opts && opts.noAuth ? true : !!(SI.state.token || SI.state.username);
  }
  function fakeQr() {
    var cells = '', x, y, on;
    for (y = 0; y < 25; y++) {
      for (x = 0; x < 25; x++) {
        var finder = (x < 8 && y < 8) || (x > 16 && y < 8) || (x < 8 && y > 16);
        on = finder ? ((x % 7 === 0 || y % 7 === 0 || (x > 1 && x < 6 && y > 1 && y < 6)))
                    : ((x * y + x * 7 + y * 13 + x * x) % 5 < 2);
        if (on) cells += '<rect x="' + x + '" y="' + y + '" width="1" height="1"/>';
      }
    }
    var svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 25 25" width="220" height="220"><rect width="25" height="25" fill="#ffffff"/><g fill="#0a0f14">' + cells + '</g></svg>';
    return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg);
  }
  function pushEvent(type, player, text, detail) {
    db.eventSeq += 1;
    var at = Date.now();
    db.events.push({ id: db.eventSeq, at: at, time: SI.fmtTime(at), type: type, player: player, text: text, detail: detail || '' });
    saveDB(db);
  }
  function progress(id) {
    setTimeout(function () {
      var e = db.entries.filter(function (x) { return x.id === id; })[0];
      if (e && e.state === 'QUEUED') { e.state = 'PLAYING'; e.playing = 1; saveDB(db); }
    }, 1400);
    setTimeout(function () {
      var e = db.entries.filter(function (x) { return x.id === id; })[0];
      if (e && (e.state === 'PLAYING' || e.state === 'QUEUED')) { e.state = 'DONE'; e.playing = 0; saveDB(db); }
    }, 16000);
  }

  var handlers = {
    '/api/health': function () {
      return { ok: true, apiVersion: 1, name: server().name, url: server().url,
        online: db.players.length, tokenAuth: true, allowBroadcast: true, auth: ['cookie', 'bearer'],
        musicSource: 'netease', externalMusicEnabled: false };
    },
    '/api/login': function (o) {
      var b = o.body || {};
      if (!b.username || !b.password) return { error: '请输入玩家名和密码', status: 400 };
      if (String(b.password).length < 6) return { error: '密码至少 6 位', status: 401 };
      return { ok: true, loggedIn: true, token: 'demo_' + Math.random().toString(36).slice(2, 12),
        tokenType: 'Bearer', expiresIn: 43200, user: user(), netease: Object.assign({}, db.netease), server: server() };
    },
    '/api/logout': function () { return { ok: true }; },
    '/api/me': function () {
      return needAuth() ? me() : { ok: true, loggedIn: false, server: server() };
    },
    '/api/password': function (o) {
      var b = o.body || {};
      if (!b.oldPassword) return { error: '请输入原密码', status: 400 };
      if (!b.newPassword || String(b.newPassword).length < 6) return { error: '新密码至少 6 位', status: 400 };
      if (b.oldPassword === b.newPassword) return { error: '新密码和原密码一样', status: 400 };
      return { ok: true, message: '密码已更新，下次登录请使用新密码' };
    },
    '/api/netease/status': function () { return { ok: true, netease: Object.assign({}, db.netease) }; },
    '/api/netease/qr': function () { db.qrPollCount = 0; saveDB(db); return { ok: true, key: 'demo-qr-key', image: fakeQr() }; },
    '/api/netease/qr/poll': function () {
      db.qrPollCount += 1; saveDB(db);
      if (db.qrPollCount >= 3) {
        db.netease = { enabled: true, bound: true, nickname: '夜航电台' }; saveDB(db);
        return { ok: true, status: 'CONFIRMED', message: '扫码成功', nickname: db.netease.nickname, netease: Object.assign({}, db.netease) };
      }
      return { ok: true, status: db.qrPollCount === 2 ? 'SCANNED' : 'WAITING', message: db.qrPollCount === 2 ? '已扫描，请在手机上确认' : '等待扫码' };
    },
    '/api/netease/login': function (o) {
      var b = o.body || {};
      if (!b.phone || !b.password) return { error: '请输入手机号和密码', status: 400 };
      db.netease = { enabled: true, bound: true, nickname: '网易云用户' + String(b.phone).slice(-4) }; saveDB(db);
      return { ok: true, nickname: db.netease.nickname, netease: Object.assign({}, db.netease) };
    },
    '/api/netease/cookie': function (o) {
      var b = o.body || {};
      if (!b.musicU) return { error: '请填写 MUSIC_U', status: 400 };
      db.netease = { enabled: true, bound: true, nickname: 'Cookie 用户' }; saveDB(db);
      return { ok: true, nickname: db.netease.nickname, netease: Object.assign({}, db.netease) };
    },
    '/api/netease/logout': function () {
      db.netease = { enabled: true, bound: false, nickname: '' }; saveDB(db);
      return { ok: true, netease: Object.assign({}, db.netease) };
    },
    '/api/players': function () { return { ok: true, players: db.players.slice(), allowBroadcast: true }; },
    '/api/search': function (o) {
      var q = String((o.params && o.params.q) || '').trim().toLowerCase();
      var list = !q ? [] : db.songs.filter(function (s) {
        return (s.name + ' ' + s.artist + ' ' + s.album + ' ' + s.id).toLowerCase().indexOf(q) >= 0;
      });
      return { ok: true, songs: list, neteaseBound: db.netease.bound };
    },
    '/api/playlists': function (o) {
      var q = String((o.params && o.params.q) || '').trim().toLowerCase();
      var list = !q ? db.playlists.slice() : db.playlists.filter(function (p) {
        return (p.name + ' ' + p.creator + ' ' + p.description).toLowerCase().indexOf(q) >= 0;
      });
      if (!list.length) list = db.playlists.slice();
      var limit = Math.min(50, parseInt((o.params && o.params.limit) || 20, 10) || 20);
      return { ok: true, playlists: list.slice(0, limit), neteaseBound: db.netease.bound };
    },
    '/api/playlist': function (o) {
      var id = Number((o.params && o.params.id) || 0);
      var pl = db.playlists.filter(function (p) { return p.id === id; })[0] || db.playlists[0];
      var limit = Math.min(500, parseInt((o.params && o.params.limit) || 100, 10) || 100);
      var offset = Number(id) % 4;
      var songs = [];
      for (var i = 0; i < Math.min(limit, db.songs.length); i++) songs.push(db.songs[(i + offset) % db.songs.length]);
      return { ok: true, playlist: pl, songs: songs, neteaseBound: db.netease.bound };
    },
    '/api/liked': function (o) {
      if (!db.netease.bound) return { error: '先在「账号设置 → 网易云账号」绑定，才能读我喜欢的音乐', status: 400 };
      var limit = Math.min(200, parseInt((o.params && o.params.limit) || 100, 10) || 100);
      var offset = Math.max(0, parseInt((o.params && o.params.offset) || 0, 10) || 0);
      return { ok: true, songs: db.songs.slice(0, limit), offset: offset, limit: limit, neteaseBound: true };
    },
    '/api/request': function (o) {
      var b = o.body || {};
      var target = String(b.target || 'self');
      var title = String(b.name || '未知歌曲'), artist = String(b.artist || '未知歌手');
      var song = db.songs.filter(function (s) { return s.id === Number(b.id); })[0];
      if (song && song.state === 'NONE') return { error: '《' + song.name + '》无版权，无法播放', status: 400 };
      if (target !== '__all__' && target !== 'self') {
        var p = db.players.filter(function (x) { return x.name.toLowerCase() === target.toLowerCase(); })[0];
        if (!p) return { error: '玩家 ' + target + ' 不在线', status: 400 };
        if (!p.hasCu) return { error: p.name + ' 的客户端没有安装 Cu 模组', status: 400 };
      }
      if (!String(b.url || '').match(/^https?:\/\//i)) return { error: '只支持 http/https 音频地址', status: 400 };
      /* 演示开关：模拟「目标玩家不在线」，用来体验网页自动播放分支 */
      if (SI.state.simulateOffline) {
        return { error: '玩家 ' + (target === 'self' ? user().name : target) + ' 不在线', status: 400 };
      }
      var wait = 2000 - (Date.now() - db.lastRequestAt);
      if (wait > 0) return { error: '点歌太快了，请 ' + Math.ceil(wait / 1000) + ' 秒后再试', status: 429 };
      db.lastRequestAt = Date.now();
      var id = Math.random().toString(36).slice(2, 12);
      var broadcast = target === '__all__';
      db.entries.unshift({
        id: id, player: user().name, target: broadcast ? '' : (target === 'self' ? user().name : target),
        title: title, artist: artist, state: 'QUEUED', message: '', playing: 0,
        listeners: broadcast ? db.players.length : 1, broadcast: broadcast,
        requestedAt: Date.now(), mine: true
      });
      if (db.entries.length > 40) db.entries.length = 40;
      saveDB(db); progress(id);
      return { ok: true, requestId: id, title: title, broadcast: broadcast,
        target: broadcast ? '' : (target === 'self' ? user().name : target), source: b.id ? 'netease' : 'direct' };
    },
    '/api/entries': function () {
      return { ok: true, entries: db.entries.map(function (e) {
        return Object.assign({}, e, { mine: e.player === user().name });
      }), online: db.players.length };
    },
    '/api/message': function (o) {
      var b = o.body || {};
      var text = String(b.text || '').replace(/\s+/g, ' ').trim().slice(0, 120);
      if (!text) return { error: '先写点什么吧', status: 400 };
      var wait = 3000 - (Date.now() - db.lastMessageAt);
      if (wait > 0) return { error: '发得太快了，请 ' + Math.ceil(wait / 1000) + ' 秒后再试', status: 429 };
      var target = String(b.target || 'self');
      if (target !== '__all__' && target !== 'self') {
        var p = db.players.filter(function (x) { return x.name.toLowerCase() === target.toLowerCase(); })[0];
        if (!p) return { error: '玩家 ' + target + ' 不在线', status: 400 };
      }
      db.lastMessageAt = Date.now();
      db.chatSeq += 1;
      var line = { id: db.chatSeq, name: user().name, text: text, source: 'web', at: Date.now(), mine: true, avatar: '' };
      db.messages.unshift(line);
      if (db.messages.length > 80) db.messages.length = 80;
      pushEvent('web', user().name, text, '');
      saveDB(db);
      return { ok: true, id: line.id, text: text, target: b.target || 'self',
        broadcast: target === '__all__', at: line.at };
    },
    '/api/chat': function () {
      return { ok: true, messages: db.messages.map(function (m) {
        return Object.assign({}, m, { mine: m.name === user().name });
      }), maxLength: 120 };
    },
    '/api/events': function (o) {
      var since = Number((o.params && o.params.since) || 0);
      var limit = Math.min(400, parseInt((o.params && o.params.limit) || 100, 10) || 100);
      var types = String((o.params && o.params.types) || '').split(',').filter(Boolean);
      var list = db.events.filter(function (e) {
        return e.id > since && (!types.length || types.indexOf(e.type) >= 0);
      }).sort(function (a, b) { return a.id - b.id; }).slice(-limit);
      return { ok: true, events: list, latest: db.eventSeq, enabled: true,
        recorded: ['chat', 'web', 'join', 'quit', 'death', 'command'] };
    }
  };

  SI.mock = {
    db: db,
    reseed: function () { db = seed(); saveDB(db); SI.mock.db = db; },
    handle: async function (path, opts) {
      var latency = Math.round(90 + Math.random() * 130);
      await SI.sleep(latency);
      var h = handlers[path];
      var out;
      if (!h) out = { error: '演示模式未实现该接口：' + path, status: 404 };
      else if (path !== '/api/health' && path !== '/api/login' && !needAuth(opts)) {
        out = { error: '请先登录（401）', status: 401 };
      } else out = h(opts);

      // 「status」在业务响应里也可能是字符串（扫码轮询 WAITING/CONFIRMED），
      // 只有数字型才代表 HTTP 状态码，不能被吞掉。
      var status = typeof out.status === 'number' ? out.status : 200;
      var data = Object.assign({}, out);
      if (typeof data.status === 'number') delete data.status;
      if (status >= 400) { delete data.ok; data.ok = false; }
      else if (!Object.prototype.hasOwnProperty.call(data, 'ok')) data.ok = true;
      return { ok: status < 400, data: data, status: status, ms: latency };
    }
  };
})();

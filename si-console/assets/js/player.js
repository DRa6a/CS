/* ============================================================
   点歌台 · 本机网页播放器
   点歌目标不在线时，服务端会拒绝；这时改由网页自己播这首歌。
   真实模式用歌曲直链（Audio），演示模式用 Web Audio 合成一段柔和和弦，
   两种情况都不依赖外网音频文件，也不会因为 403 让功能看起来是坏的。
   ============================================================ */
(function () {
  'use strict';
  var SI = window.SI;

  var st = {
    song: null, playing: false, elapsed: 0, duration: 240000,
    source: '', reason: '', volume: .7, error: ''
  };
  var audio = null, ctx = null, master = null, voices = [];
  var timer = null, startedAt = 0, baseElapsed = 0;

  SI.webPlayerState = function () { return Object.assign({}, st); };
  function emit() { SI.emit('web-player', Object.assign({}, st)); }

  function tick() {
    if (!st.playing) return;
    st.elapsed = baseElapsed + (Date.now() - startedAt);
    if (st.elapsed >= st.duration) {
      st.elapsed = st.duration;
      stopAll();
    }
    emit();
  }
  function startTimer() {
    if (timer) clearInterval(timer);
    startedAt = Date.now();
    timer = setInterval(tick, 500);
  }
  function stopTimer() { if (timer) { clearInterval(timer); timer = null; } }

  /* ------------------------------------------------------ 演示音源 */
  function ensureCtx() {
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    if (!ctx) {
      ctx = new AC();
      master = ctx.createGain();
      master.gain.value = 0;
      master.connect(ctx.destination);
    }
    if (ctx.state === 'suspended' && ctx.resume) ctx.resume();
    return ctx;
  }
  function demoStopNodes() {
    if (!ctx || !master) return;
    var now = ctx.currentTime;
    try { master.gain.cancelScheduledValues(now); master.gain.setTargetAtTime(0, now, .08); } catch (e) {}
    voices.forEach(function (v) {
      try { v.o.stop(now + .3); } catch (e) {}
      try { v.l.stop(now + .3); } catch (e) {}
    });
    voices = [];
  }
  function demoStart() {
    var c = ensureCtx();
    if (!c) return false;
    demoStopNodes();
    var now = c.currentTime;
    master.gain.cancelScheduledValues(now);
    master.gain.setValueAtTime(0, now);
    master.gain.linearRampToValueAtTime(.035 * st.volume, now + .6);
    var freqs = [261.63, 329.63, 392.00, 523.25];
    var levels = [.5, .32, .26, .14];
    voices = freqs.map(function (f, i) {
      var o = c.createOscillator(), g = c.createGain();
      o.type = 'sine'; o.frequency.value = f; g.gain.value = levels[i];
      o.connect(g); g.connect(master); o.start();
      var lfo = c.createOscillator(), lg = c.createGain();
      lfo.frequency.value = .12 + i * .03; lg.gain.value = .06;
      lfo.connect(lg); lg.connect(g.gain); lfo.start();
      return { o: o, g: g, l: lfo };
    });
    return true;
  }

  /* ------------------------------------------------------ 真实直链 */
  function fallbackToDemo() {
    st.source = '本机播放 · 演示音源';
    st.error = '音频直链不可用，已切到本机演示音';
    if (demoStart()) { st.playing = true; startTimer(); }
    else { st.playing = false; st.error = '浏览器不支持音频播放'; }
    emit();
  }
  function urlStart(url) {
    try {
      if (!audio) {
        audio = new Audio();
        audio.preload = 'auto';
        audio.addEventListener('ended', function () { stopAll(); emit(); });
        audio.addEventListener('error', function () { if (st.playing) fallbackToDemo(); });
      }
      audio.volume = st.volume;
      audio.src = url;
      var p = audio.play();
      if (p && p.catch) p.catch(function () { fallbackToDemo(); });
      return true;
    } catch (e) { return false; }
  }
  function urlPause() { if (audio) { try { audio.pause(); } catch (e) {} } }
  function urlResume() {
    if (audio && audio.src) {
      var p = audio.play();
      if (p && p.catch) p.catch(function () { fallbackToDemo(); });
    }
  }
  function stopAll() {
    urlPause(); demoStopNodes(); stopTimer();
    st.playing = false;
  }

  /* ------------------------------------------------------ 对外接口 */
  SI.webPlay = function (song, reason) {
    if (!song) return false;
    stopAll();
    st.song = {
      name: song.name || song.title || '未知歌曲',
      artist: song.artist || '', album: song.album || '',
      cover: song.cover || '', url: song.url || ''
    };
    st.duration = Number(song.duration) || 240000;
    st.elapsed = 0; baseElapsed = 0; st.error = '';
    st.reason = reason || '';
    st.source = reason || '本机播放';

    if (SI.state.demo) {
      st.source = '本机播放 · 演示音源';
      if (demoStart()) { st.playing = true; startTimer(); }
      else st.error = '浏览器不支持音频播放';
    } else if (song.url && /^https?:\/\//i.test(song.url)) {
      st.source = '本机播放 · 音频直链';
      if (urlStart(song.url)) { st.playing = true; startTimer(); }
      else fallbackToDemo();
    } else {
      fallbackToDemo();
    }
    emit();
    return st.playing;
  };
  SI.webToggle = function () {
    if (!st.song) return false;
    if (st.playing) {
      baseElapsed = st.elapsed;
      stopAll();
    } else {
      if (SI.state.demo) { if (demoStart()) { st.playing = true; startTimer(); } }
      else { urlResume(); st.playing = true; startTimer(); }
    }
    emit();
    return st.playing;
  };
  SI.webStop = function () { stopAll(); st.elapsed = 0; baseElapsed = 0; emit(); };
  SI.webSeek = function (ratio) {
    if (!st.song) return;
    st.elapsed = Math.max(0, Math.min(st.duration, st.duration * Number(ratio || 0)));
    baseElapsed = st.elapsed; startedAt = Date.now();
    if (audio && audio.duration && !SI.state.demo) {
      try { audio.currentTime = st.elapsed / 1000; } catch (e) {}
    }
    emit();
  };
  SI.webVolume = function (v) {
    st.volume = Math.max(0, Math.min(1, Number(v)));
    if (audio) audio.volume = st.volume;
    if (ctx && master) { try { master.gain.setTargetAtTime(.035 * st.volume, ctx.currentTime, .05); } catch (e) {} }
    emit();
  };
})();

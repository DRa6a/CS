(() => {
	'use strict';

	const $ = (id) => document.getElementById(id);
	const state = {
		user: null,
		server: null,
		netease: { enabled: true, bound: false, nickname: '' },
		songs: [],
		entries: [],
		messages: [],
		players: [],
		pollTimer: null,
		playerTimer: null,
		msgTimer: null,
		searchTimer: null,
		qrTimer: null,
		qrKey: null,
		nmMode: 'qr',
		lastQuery: '',
		lastPlayersKey: '',
		lastEntriesKey: '',
		lastMessagesKey: ''
	};
	// ------------------------------------------------------------ 网络

	async function api(path, options) {
		const opts = Object.assign({ headers: {} }, options || {});
		if (opts.body && typeof opts.body !== 'string') {
			opts.headers['Content-Type'] = 'application/json';
			opts.body = JSON.stringify(opts.body);
		}
		const response = await fetch(path, opts);
		// 服务端内容没变会回 304，浏览器把它还原成缓存里的那份；真拿到裸 304 就当没变化
		if (response.status === 304) {
			return { ok: true, notModified: true };
		}
		let data = null;
		try {
			data = await response.json();
		} catch (e) {
			data = { ok: false, error: '服务器返回异常' };
		}
		if (!response.ok && data && !data.error) {
			data.error = 'HTTP ' + response.status;
		}
		return data || { ok: false, error: '空响应' };
	}

	// ------------------------------------------------------------ 提示

	let toastTimer = null;

	function toast(message, type) {
		const el = $('toast');
		el.textContent = message;
		el.className = 'toast show' + (type ? ' ' + type : '');
		clearTimeout(toastTimer);
		toastTimer = setTimeout(() => {
			el.className = 'toast';
		}, type === 'error' ? 4600 : 2800);
	}

	// ------------------------------------------------------------ 视图

	function showAuth() {
		$('authView').hidden = false;
		$('mainView').hidden = true;
		$('userBox').hidden = true;
		$('settingsModal').hidden = true;
		stopQr(true);
		stopTimers();
	}

	function showMain() {
		$('authView').hidden = true;
		$('mainView').hidden = false;
		$('userBox').hidden = false;
		$('userName').textContent = state.user.name;
		const avatar = $('avatar');
		avatar.style.visibility = '';
		if (state.user.avatar) {
			avatar.src = state.user.avatar;
			avatar.onerror = () => {
				avatar.style.visibility = 'hidden';
			};
		} else {
			avatar.style.visibility = 'hidden';
		}
		renderNetease();
		refreshPlayers();
		refreshEntries();
		refreshChat();
		stopTimers();
		state.pollTimer = setInterval(refreshEntries, 3000);
		state.playerTimer = setInterval(refreshPlayers, 10000);
		state.msgTimer = setInterval(refreshChat, 3000);
	}

	function stopTimers() {
		if (state.pollTimer) {
			clearInterval(state.pollTimer);
			state.pollTimer = null;
		}
		if (state.playerTimer) {
			clearInterval(state.playerTimer);
			state.playerTimer = null;
		}
		if (state.msgTimer) {
			clearInterval(state.msgTimer);
			state.msgTimer = null;
		}
	}

	function updateServerBadge() {
		const online = state.server ? state.server.online : 0;
		$('onlineBadge').textContent = '在线 ' + online;
		if (state.server && state.server.name) {
			$('serverTitle').textContent = state.server.name;
		}
	}

	// ------------------------------------------------------------ 登录

	async function loadMe() {
		const data = await api('/api/me');
		if (data.server) {
			state.server = data.server;
			updateServerBadge();
			if (data.server.url) {
				$('serverSub').textContent = '登录后即可为游戏里的玩家点歌 · ' + data.server.url;
			}
		}
		if (data.loggedIn) {
			state.user = data.user;
			if (data.netease) state.netease = data.netease;
			showMain();
		} else {
			showAuth();
		}
	}

	function bindAuth() {
		$('loginForm').addEventListener('submit', async (event) => {
			event.preventDefault();
			const button = $('loginForm').querySelector('button[type=submit]');
			button.disabled = true;
			const data = await api('/api/login', {
				method: 'POST',
				body: { username: $('loginName').value.trim(), password: $('loginPass').value }
			});
			button.disabled = false;
			if (data.ok) {
				state.user = data.user;
				state.server = data.server;
				if (data.netease) state.netease = data.netease;
				toast('欢迎回来，' + data.user.name, 'ok');
				showMain();
				updateServerBadge();
			} else {
				toast(data.error || '登录失败', 'error');
			}
		});

		$('logoutBtn').addEventListener('click', async () => {
			await api('/api/logout', { method: 'POST' });
			state.user = null;
			showAuth();
			toast('已退出登录');
		});
	}

	// ------------------------------------------------------------ 设置

	function openSettings() {
		if (!state.user) return;
		$('passwordForm').reset();
		$('neteaseForm').reset();
		$('nmCookie').value = '';
		$('settingsModal').hidden = false;
		renderNetease();
	}

	function closeSettings() {
		$('settingsModal').hidden = true;
		stopQr(true);
	}

	// ------------------------------------------------------------ 网易云

	function setNeteaseMode(mode) {
		state.nmMode = mode;
		$('qrBox').hidden = mode !== 'qr';
		$('neteaseForm').hidden = mode !== 'phone';
		$('cookieBox').hidden = mode !== 'cookie';
		$('modeQr').hidden = mode === 'qr';
		$('modePhone').hidden = mode === 'phone';
		$('modeCookie').hidden = mode === 'cookie';
		if (mode === 'qr') {
			ensureQr(false);
		} else {
			stopQr(true);
		}
	}

	async function ensureQr(force) {
		if (state.qrKey && !force) return;
		stopQr(true);
		$('qrImage').removeAttribute('src');
		$('qrHint').textContent = '正在获取二维码…';
		$('qrRefresh').hidden = true;
		const data = await api('/api/netease/qr', { method: 'POST' });
		if (!data.ok) {
			$('qrHint').textContent = data.error || '二维码获取失败';
			$('qrRefresh').hidden = false;
			return;
		}
		state.qrKey = data.key;
		$('qrImage').src = data.image;
		$('qrHint').textContent = '打开网易云 App → 扫一扫';
		state.qrTimer = setInterval(pollQr, 2000);
	}

	async function pollQr() {
		if (!state.qrKey) return;
		const data = await api('/api/netease/qr/poll?key=' + encodeURIComponent(state.qrKey));
		if (!data.ok) {
			stopQr(true);
			$('qrHint').textContent = data.error || '二维码已失效';
			$('qrRefresh').hidden = false;
			return;
		}
		if (data.status === 'SCANNED') {
			$('qrHint').textContent = '已扫码，请在手机上点「确认登录」';
		} else if (data.status === 'EXPIRED') {
			stopQr(true);
			$('qrHint').textContent = '二维码已过期';
			$('qrRefresh').hidden = false;
		} else if (data.status === 'CONFIRMED') {
			stopQr(true);
			state.netease = data.netease;
			renderNetease();
			toast('已绑定网易云：' + (data.nickname || ''), 'ok');
		}
	}

	function stopQr(clearKey) {
		if (state.qrTimer) {
			clearInterval(state.qrTimer);
			state.qrTimer = null;
		}
		if (clearKey) state.qrKey = null;
	}

	function renderNetease() {
		const netease = state.netease || {};
		const bound = !!netease.bound;
		$('neteaseState').textContent = bound
			? '已绑定：' + (netease.nickname || '网易云用户') + ' · VIP 歌曲可以正常播放'
			: '未绑定 · 绑定后可以播放 VIP 歌曲';
		$('neteaseUnbind').hidden = !bound;

		$('qrBox').hidden = true;
		$('neteaseForm').hidden = true;
		$('cookieBox').hidden = true;
		$('modeQr').hidden = true;
		$('modePhone').hidden = true;
		$('modeCookie').hidden = true;

		if (bound || !netease.enabled) {
			stopQr(true);
		} else if (!$('settingsModal').hidden) {
			setNeteaseMode('qr');
		}

		const tip = $('neteaseTip');
		if (!netease.enabled || bound) {
			tip.hidden = true;
		} else {
			tip.hidden = false;
			$('neteaseTipText').textContent = '未绑定网易云 · VIP 歌曲无法播放，点此绑定';
		}
	}

	function bindSettings() {
		$('settingsBtn').addEventListener('click', openSettings);
		$('neteaseTip').addEventListener('click', openSettings);
		$('closeSettings').addEventListener('click', closeSettings);
		$('settingsModal').addEventListener('click', (event) => {
			if (event.target === $('settingsModal')) closeSettings();
		});

		$('passwordForm').addEventListener('submit', async (event) => {
			event.preventDefault();
			if ($('newPass').value !== $('newPass2').value) {
				toast('两次输入的新密码不一致', 'error');
				return;
			}
			const button = $('passwordForm').querySelector('button[type=submit]');
			button.disabled = true;
			const data = await api('/api/password', {
				method: 'POST',
				body: { oldPassword: $('oldPass').value, newPassword: $('newPass').value }
			});
			button.disabled = false;
			if (data.ok) {
				$('passwordForm').reset();
				toast(data.message || '密码已更新', 'ok');
			} else {
				toast(data.error || '修改失败', 'error');
			}
		});

		$('modeQr').addEventListener('click', () => setNeteaseMode('qr'));
		$('modePhone').addEventListener('click', () => setNeteaseMode('phone'));
		$('modeCookie').addEventListener('click', () => setNeteaseMode('cookie'));
		$('qrRefresh').addEventListener('click', () => ensureQr(true));

		$('neteaseForm').addEventListener('submit', async (event) => {
			event.preventDefault();
			const phone = $('nmPhone').value.trim();
			const password = $('nmPass').value;
			if (!phone || !password) {
				toast('请输入网易云手机号和密码', 'error');
				return;
			}
			const button = $('neteaseForm').querySelector('button[type=submit]');
			button.disabled = true;
			button.innerHTML = '<span class="spin"></span>';
			const data = await api('/api/netease/login', {
				method: 'POST',
				body: { phone: phone, password: password }
			});
			button.disabled = false;
			button.textContent = '登录网易云';
			if (data.ok) {
				state.netease = data.netease;
				$('neteaseForm').reset();
				renderNetease();
				toast('已绑定网易云：' + (data.nickname || ''), 'ok');
			} else {
				toast(data.error || '登录失败', 'error');
			}
		});

		$('cookieSave').addEventListener('click', async () => {
			const musicU = $('nmCookie').value.trim();
			if (!musicU) {
				toast('请先粘贴 Cookie', 'error');
				return;
			}
			const data = await api('/api/netease/cookie', {
				method: 'POST',
				body: { musicU: musicU }
			});
			if (data.ok) {
				state.netease = data.netease;
				$('nmCookie').value = '';
				renderNetease();
				toast('已绑定网易云：' + (data.nickname || ''), 'ok');
			} else {
				toast(data.error || 'Cookie 无效', 'error');
			}
		});

		$('neteaseUnbind').addEventListener('click', async () => {
			const data = await api('/api/netease/logout', { method: 'POST' });
			if (data.ok) {
				state.netease = data.netease;
				renderNetease();
				toast('已解除网易云绑定');
			}
		});
	}

	// ------------------------------------------------------------ 点给谁

	function currentTarget() {
		const select = $('targetSelect');
		return select && select.value ? select.value : '';
	}

	async function refreshPlayers() {
		const data = await api('/api/players');
		if (!data.ok) {
			if (data.error === '请先登录') showAuth();
			return;
		}
		if (data.notModified) return;
		state.players = data.players || [];
		state.server = state.server || {};
		state.server.allowBroadcast = data.allowBroadcast;

		const previous = currentTarget();
		const options = [];
		state.players.forEach((player) => {
			options.push({
				value: player.name,
				label: (player.self ? '我自己（' + player.name + '）' : player.name)
					+ (player.hasCu ? '' : ' · 未装客户端'),
				disabled: !player.hasCu
			});
		});
		if (data.allowBroadcast) {
			options.push({ value: '__all__', label: '全服播放', disabled: false });
		}

		const key = JSON.stringify(options);
		if (key === state.lastPlayersKey && $('targetSelect').options.length > 0) {
			return;
		}
		state.lastPlayersKey = key;

		const select = $('targetSelect');
		select.textContent = '';
		options.forEach((option) => {
			const el = document.createElement('option');
			el.value = option.value;
			el.textContent = option.label;
			el.disabled = option.disabled;
			select.appendChild(el);
		});

		const values = options.filter((o) => !o.disabled).map((o) => o.value);
		if (values.includes(previous)) {
			select.value = previous;
		} else if (state.user && values.includes(state.user.name)) {
			select.value = state.user.name;
		} else if (values.length > 0) {
			select.value = values[0];
		}

		if (values.length === 0) {
			const el = document.createElement('option');
			el.value = '';
			el.textContent = '当前没有在线玩家';
			select.appendChild(el);
		}
		updateMsgHint();
	}

	// ------------------------------------------------------------ 搜索

	function formatDuration(ms) {
		if (!ms || ms < 0) return '';
		const total = Math.round(ms / 1000);
		const m = Math.floor(total / 60);
		const s = total % 60;
		return m + ':' + String(s).padStart(2, '0');
	}

	function coverNode(song) {
		const box = document.createElement('div');
		box.className = 'cover';
		box.textContent = (song.name || '?').trim().charAt(0);
		if (song.cover) {
			const img = document.createElement('img');
			img.alt = '';
			img.loading = 'lazy';
			img.src = song.cover.replace(/^http:\/\//, location.protocol === 'https:' ? 'https://' : 'http://');
			img.onerror = () => img.remove();
			box.appendChild(img);
		}
		return box;
	}

	function renderSongs() {
		const list = $('songList');
		list.textContent = '';
		$('resultHead').hidden = state.songs.length === 0;
		if (state.songs.length === 0) {
			if (state.lastQuery) {
				const empty = document.createElement('li');
				empty.className = 'empty';
				empty.textContent = '没有找到「' + state.lastQuery + '」，换个关键词试试';
				list.appendChild(empty);
			}
			return;
		}
		state.songs.forEach((song) => {
			const li = document.createElement('li');
			li.className = 'song';
			li.appendChild(coverNode(song));

			const main = document.createElement('div');
			main.className = 'song-main';
			const title = document.createElement('div');
			title.className = 'song-title';
			title.textContent = song.name;
			const sub = document.createElement('div');
			sub.className = 'song-sub';
			sub.textContent = song.artist + (song.album ? ' · ' + song.album : '');
			main.appendChild(title);
			main.appendChild(sub);
			li.appendChild(main);

			if (song.state === 'LOGIN' || song.state === 'NONE') {
				const tag = document.createElement('span');
				tag.className = 'tag ' + (song.state === 'LOGIN' ? 'tag-login' : 'tag-none');
				tag.textContent = song.state === 'LOGIN' ? 'VIP' : '无版权';
				if (song.state === 'LOGIN') tag.title = '绑定网易云账号后可播放';
				li.appendChild(tag);
			}

			const dur = document.createElement('span');
			dur.className = 'song-dur';
			dur.textContent = formatDuration(song.duration);
			li.appendChild(dur);

			const button = document.createElement('button');
			button.className = 'btn ghost small';
			if (song.state === 'NONE') {
				button.textContent = '不可播';
				button.disabled = true;
				button.title = '网易云上没有这首歌的可播放音频';
			} else {
				button.textContent = '点歌';
				button.addEventListener('click', () => requestSong({
					id: song.id,
					name: song.name,
					artist: song.artist,
					url: song.url
				}, button));
			}
			li.appendChild(button);

			list.appendChild(li);
		});
	}

	// 边打字边搜会并发好几个请求，回来顺序不一定对，用序号只认最后一次
	let searchSeq = 0;

	async function search() {
		const query = $('searchInput').value.trim();
		state.lastQuery = query;
		if (!query) {
			searchSeq++;
			state.songs = [];
			renderSongs();
			return;
		}
		const seq = ++searchSeq;
		$('searchBtn').innerHTML = '<span class="spin"></span>';
		const data = await api('/api/search?q=' + encodeURIComponent(query));
		if (seq !== searchSeq) return;
		$('searchBtn').textContent = '搜索';
		if (!data.ok) {
			toast(data.error || '搜索失败', 'error');
			return;
		}
		state.songs = data.songs || [];
		renderSongs();
		if (state.songs.length === 0) {
			toast('没有搜到结果');
		}
	}

	// ------------------------------------------------------------ 点歌

	async function requestSong(song, button) {
		const target = currentTarget();
		if (!target) {
			toast('先选择一位在线玩家', 'error');
			return;
		}
		if (button) button.disabled = true;
		const data = await api('/api/request', {
			method: 'POST',
			body: {
				id: song.id || 0,
				name: song.name || '',
				artist: song.artist || '',
				url: song.url || '',
				target: target
			}
		});
		if (button) button.disabled = false;
		if (data.ok) {
			const where = data.broadcast ? '全服' : (data.target || '');
			const tag = data.source === 'fallback' ? '（备用源）' : '';
			toast('已推送给 ' + where + tag + '：' + data.title, 'ok');
			refreshEntries();
		} else {
			toast(data.error || '点歌失败', 'error');
			if (data.error && data.error.indexOf('不在线') >= 0) refreshPlayers();
		}
	}

	// ------------------------------------------------------------ 记录

	const STATE_TEXT = {
		QUEUED: '排队中',
		PLAYING: '播放中',
		DONE: '已播完',
		FAILED: '失败'
	};

	async function refreshEntries() {
		const data = await api('/api/entries');
		if (!data.ok) {
			if (data.error === '请先登录') showAuth();
			return;
		}
		if (data.notModified) return;
		state.entries = data.entries || [];
		// 内容没变就不重绘：每 3 秒把列表拆了重建既打断滚动，也白烧 CPU
		const key = JSON.stringify(state.entries);
		if (key !== state.lastEntriesKey) {
			state.lastEntriesKey = key;
			renderEntries();
		}
		if (typeof data.online === 'number' && state.server) {
			state.server.online = data.online;
			updateServerBadge();
		}
	}

	function renderEntries() {
		const list = $('entryList');
		list.textContent = '';
		if (state.entries.length === 0) {
			const empty = document.createElement('li');
			empty.className = 'empty';
			empty.textContent = '还没有人点歌，来当第一个吧';
			list.appendChild(empty);
			$('entriesHint').textContent = '';
			return;
		}
		$('entriesHint').textContent = state.entries.length + ' 条';
		state.entries.forEach((entry) => {
			const li = document.createElement('li');
			li.className = 'entry' + (entry.mine ? ' mine' : '');

			const main = document.createElement('div');
			main.className = 'entry-main';
			const title = document.createElement('div');
			title.className = 'entry-title';
			title.textContent = entry.title;
			const sub = document.createElement('div');
			sub.className = 'entry-sub';
			let subText = entry.artist + ' · ' + entry.player;
			if (entry.broadcast) {
				subText += ' → 全服';
			} else if (entry.target) {
				subText += ' → ' + entry.target;
			}
			if (entry.state === 'FAILED' && entry.message) subText += ' · ' + entry.message;
			sub.textContent = subText;
			main.appendChild(title);
			main.appendChild(sub);
			li.appendChild(main);

			const badge = document.createElement('span');
			badge.className = 'badge ' + entry.state;
			badge.textContent = STATE_TEXT[entry.state] || entry.state;
			li.appendChild(badge);

			list.appendChild(li);
		});
	}

	// ------------------------------------------------------------ 游戏内对话

	const MAX_MSG = 120;

	async function refreshChat() {
		const data = await api('/api/chat');
		if (!data.ok) {
			if (data.error === '请先登录') showAuth();
			return;
		}
		if (data.notModified) return;
		state.messages = data.messages || [];
		const key = JSON.stringify(state.messages);
		if (key === state.lastMessagesKey) return;
		state.lastMessagesKey = key;
		renderChat();
	}

	function renderChat() {
		const list = $('chatList');
		// 只有本来就在底部时才跟着滚，别打断往上翻记录的人
		const stick = list.scrollHeight - list.scrollTop - list.clientHeight < 48;
		list.textContent = '';
		if (state.messages.length === 0) {
			const empty = document.createElement('li');
			empty.className = 'empty';
			empty.textContent = '还没有人说话';
			list.appendChild(empty);
			$('chatHint').textContent = '';
			return;
		}
		$('chatHint').textContent = state.messages.length + ' 条';

		state.messages.forEach((line) => {
			const li = document.createElement('li');
			li.className = 'chat' + (line.mine ? ' mine' : '');

			const avatar = document.createElement('span');
			avatar.className = 'chat-avatar';
			avatar.textContent = (line.name || '?').trim().charAt(0).toUpperCase();
			if (line.avatar) {
				const img = document.createElement('img');
				img.alt = '';
				img.loading = 'lazy';
				img.src = line.avatar;
				img.onerror = () => img.remove();
				avatar.appendChild(img);
			}

			const body = document.createElement('div');
			body.className = 'chat-body';

			if (line.source === 'web') {
				const tag = document.createElement('span');
				tag.className = 'chat-tag';
				tag.textContent = 'WEB';
				body.appendChild(tag);
			}

			const name = document.createElement('span');
			name.className = 'chat-name';
			name.textContent = '<' + line.name + '>';
			body.appendChild(name);

			const text = document.createElement('span');
			text.className = 'chat-text';
			text.textContent = ' ' + line.text;
			body.appendChild(text);

			li.appendChild(avatar);
			li.appendChild(body);
			list.appendChild(li);
		});

		if (stick) list.scrollTop = list.scrollHeight;
	}

	function updateMsgCount() {
		$('msgCount').textContent = $('msgInput').value.length + ' / ' + MAX_MSG;
	}

	function updateMsgHint() {
		const target = currentTarget();
		$('msgHint').textContent = !target ? '' : (target === '__all__' ? '全服' : target);
	}

	async function sendMessage() {
		const text = $('msgInput').value.trim();
		const target = currentTarget();
		if (!target) {
			toast('先选择一位在线玩家', 'error');
			return;
		}
		if (!text) {
			toast('先写点什么吧', 'error');
			$('msgInput').focus();
			return;
		}
		const button = $('msgSend');
		button.disabled = true;
		const data = await api('/api/message', {
			method: 'POST',
			body: { target: target, text: text }
		});
		button.disabled = false;
		if (data.ok) {
			$('msgInput').value = '';
			updateMsgCount();
			toast(data.broadcast ? '已发到全服聊天' : '已发给 ' + data.target, 'ok');
			refreshChat();
		} else {
			toast(data.error || '发送失败', 'error');
			if (data.error && data.error.indexOf('不在线') >= 0) refreshPlayers();
		}
	}

	// ------------------------------------------------------------ 绑定

	function bindMain() {
		$('searchBtn').addEventListener('click', search);
		$('searchInput').addEventListener('keydown', (event) => {
			if (event.key === 'Enter') {
				event.preventDefault();
				clearTimeout(state.searchTimer);
				search();
			}
		});
		$('searchInput').addEventListener('input', () => {
			clearTimeout(state.searchTimer);
			const value = $('searchInput').value.trim();
			if (value.length === 0) {
				state.songs = [];
				state.lastQuery = '';
				renderSongs();
				return;
			}
			state.searchTimer = setTimeout(search, 450);
		});
		$('clearSearch').addEventListener('click', () => {
			$('searchInput').value = '';
			state.songs = [];
			state.lastQuery = '';
			renderSongs();
		});
		$('directBtn').addEventListener('click', () => {
			$('directPanel').hidden = !$('directPanel').hidden;
		});
		$('directSend').addEventListener('click', () => {
			const url = $('directUrl').value.trim();
			if (!url) {
				toast('请填写音频直链', 'error');
				return;
			}
			requestSong({
				url: url,
				name: $('directTitle').value.trim() || '直链点歌',
				artist: ''
			}, $('directSend'));
		});

		$('targetSelect').addEventListener('change', updateMsgHint);
		$('msgSend').addEventListener('click', sendMessage);
		$('msgInput').addEventListener('input', updateMsgCount);
		$('msgInput').addEventListener('keydown', (event) => {
			if (event.key === 'Enter' && !event.shiftKey) {
				event.preventDefault();
				sendMessage();
			}
		});
	}

	// ------------------------------------------------------------ 低性能兜底

	// 开页面后量一段帧率，偏低就给 <html> 挂 perf-lite：关掉毛玻璃和极光动画。
	// 集成显卡 / 老笔记本 / 同时开着游戏的情况下，这一档能救回不少帧数。
	function detectPerf() {
		const html = document.documentElement;
		if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
			html.classList.add('perf-lite');
			return;
		}
		let frames = 0;
		let start = 0;
		const step = (now) => {
			if (!start) start = now;
			frames++;
			if (now - start < 1200) {
				requestAnimationFrame(step);
				return;
			}
			if (frames / ((now - start) / 1000) < 45) {
				html.classList.add('perf-lite');
			}
		};
		requestAnimationFrame(step);
	}

	// ------------------------------------------------------------ 启动

	bindAuth();
	bindSettings();
	bindMain();
	loadMe();
	if (document.readyState === 'complete') {
		setTimeout(detectPerf, 300);
	} else {
		window.addEventListener('load', () => setTimeout(detectPerf, 300));
	}
})();

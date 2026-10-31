package a6a.dr.si.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页登录会话（内存态）。服务端重启后玩家需要重新登录网页。
 */
public final class SessionStore {
	public record Session(String name, UUID uuid, long expiresAt, String ip) {
		public boolean expired() {
			return System.currentTimeMillis() > expiresAt;
		}
	}

	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, Session> tokens = new ConcurrentHashMap<>();
	private final long ttlMs;

	public SessionStore(int hours) {
		this.ttlMs = Math.max(1, hours) * 3_600_000L;
	}

	public String create(String name, UUID uuid, String ip) {
		byte[] raw = new byte[24];
		RANDOM.nextBytes(raw);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		tokens.put(token, new Session(name, uuid, System.currentTimeMillis() + ttlMs, ip));
		cleanup();
		return token;
	}

	public Session get(String token) {
		if (token == null || token.isEmpty()) return null;
		Session session = tokens.get(token);
		if (session == null) return null;
		if (session.expired()) {
			tokens.remove(token);
			return null;
		}
		return session;
	}

	public void remove(String token) {
		if (token != null) tokens.remove(token);
	}

	public void removeByName(String name) {
		tokens.entrySet().removeIf(e -> e.getValue().name.equalsIgnoreCase(name));
	}

	public void clear() {
		tokens.clear();
	}

	private void cleanup() {
		if (tokens.size() < 64) return;
		tokens.entrySet().removeIf(e -> e.getValue().expired());
	}
}

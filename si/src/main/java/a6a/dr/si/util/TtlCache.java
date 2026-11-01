package a6a.dr.si.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.function.Function;

/**
 * 极简 TTL + LRU 缓存。
 *
 * <p>给网易云接口用：搜索、播放地址、可播探测都是外网往返，重复请求直接命中内存。
 * 取值与装载不持锁，避免一次慢请求把所有网页线程串起来。</p>
 */
public final class TtlCache<K, V> {
	private record Entry<V>(V value, long expiresAt) {
	}

	private final int capacity;
	private final long ttlMs;
	private final LinkedHashMap<K, Entry<V>> map = new LinkedHashMap<>(64, 0.75f, true);

	public TtlCache(int capacity, long ttlMs) {
		this.capacity = Math.max(1, capacity);
		this.ttlMs = Math.max(1000L, ttlMs);
	}

	public synchronized V get(K key) {
		if (key == null) return null;
		Entry<V> entry = map.get(key);
		if (entry == null) return null;
		if (entry.expiresAt() < System.currentTimeMillis()) {
			map.remove(key);
			return null;
		}
		return entry.value();
	}

	public synchronized void put(K key, V value) {
		if (key == null || value == null) return;
		map.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMs));
		// accessOrder=true：迭代顺序就是「最久没被用到的在前」，从头部淘汰即可
		while (map.size() > capacity) {
			Iterator<K> iterator = map.keySet().iterator();
			iterator.next();
			iterator.remove();
		}
	}

	/** 缓存没有就调 loader 算一次（loader 在锁外执行，慢请求不会阻塞别人） */
	public V computeIfAbsent(K key, Function<K, V> loader) {
		V hit = get(key);
		if (hit != null) return hit;
		V value = loader.apply(key);
		if (value != null) put(key, value);
		return value;
	}

	public synchronized int size() {
		return map.size();
	}

	public synchronized void clear() {
		map.clear();
	}
}

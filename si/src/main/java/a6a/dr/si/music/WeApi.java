package a6a.dr.si.music;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网易云 weapi 参数加密。
 *
 * <p>网易云网页端把请求体做两次 AES-CBC + 一次 RSA 再提交，
 * 这里按同样的算法实现，纯 JDK，不依赖任何第三方库。</p>
 */
public final class WeApi {
	private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
	private static final String IV = "0102030405060708";
	private static final String PUB_KEY = "010001";
	private static final String MODULUS = "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b72515"
			+ "2b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3"
			+ "685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7";
	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private WeApi() {
	}

	/** 生成 weapi 需要的表单字段：params + encSecKey */
	public static Map<String, String> encrypt(String json) {
		char[] secret = new char[16];
		for (int i = 0; i < secret.length; i++) {
			secret[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
		}
		return encrypt(json, new String(secret));
	}

	/** 指定密钥版本（便于对照测试） */
	public static Map<String, String> encrypt(String json, String secretKey) {
		String first = aesEncrypt(json, PRESET_KEY);
		String params = aesEncrypt(first, secretKey);
		String encSecKey = rsaEncrypt(secretKey);
		Map<String, String> form = new LinkedHashMap<>();
		form.put("params", params);
		form.put("encSecKey", encSecKey);
		return form;
	}

	private static String aesEncrypt(String text, String key) {
		try {
			Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
			cipher.init(Cipher.ENCRYPT_MODE,
					new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
					new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
			return Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("AES 加密失败", e);
		}
	}

	private static String rsaEncrypt(String secretKey) {
		String reversed = new StringBuilder(secretKey).reverse().toString();
		BigInteger text = new BigInteger(1, reversed.getBytes(StandardCharsets.UTF_8));
		BigInteger result = text.modPow(new BigInteger(PUB_KEY, 16), new BigInteger(MODULUS, 16));
		String hex = result.toString(16);
		StringBuilder sb = new StringBuilder(256);
		for (int i = hex.length(); i < 256; i++) sb.append('0');
		sb.append(hex);
		return sb.toString();
	}
}

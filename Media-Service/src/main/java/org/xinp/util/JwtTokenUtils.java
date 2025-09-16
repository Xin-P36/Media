package org.xinp.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenUtils {
    //生成密钥
    @Value("${media.secret-string}")
    private String secretString;
    byte[] keyBytes;
    SecretKey KEY;

    @PostConstruct
    public void init() {
        keyBytes = secretString.getBytes(StandardCharsets.UTF_8);
        KEY = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    //定义过期时间
    private static final Long TIME = 604800000L; //时间7天
    /**
     * 生成Token
     *
     * @param dataMap 自定义参数
     * @param user    主题这个令牌是关于哪个用户的
     * @return 生成的Token
     */
    public String generateToken(String user, Map<String, Object> dataMap) {
        return Jwts.builder()
                .subject(user)
                .issuer("X-Media") // 签发者
                .claims(dataMap)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TIME))
                .signWith(KEY)
                .compact();
    }
    /**
     * 解析Token
     * @param token 要解析的Token
     * @return 解析结果
     * getSubject获取主题
     * getIssuer获取签发者
     * getClaims获取自定义信息
     * get("键")：获取键对应的值
     * getIssuedAt获取签发时间
     * getExpiration获取过期时间
     */
    public Claims parseToken(String token) {
        return Jwts.parser() // 创建JwtParser对象
                .verifyWith(KEY) //
                .build()
                .parseSignedClaims(token) // 解析JWT并返回Claims对象
                .getPayload();
    }
}
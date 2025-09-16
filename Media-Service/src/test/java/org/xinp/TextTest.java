package org.xinp;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.xinp.util.JwtTokenUtils;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest
public class TextTest {
    @Autowired
    private JwtTokenUtils jwtTokenUtils;
    @Test
    public void test() {
        Map<String, Object> stringObjectMap = new HashMap<>();
        stringObjectMap.put("userId", 1);
        String xinP = jwtTokenUtils.generateToken("XinP", stringObjectMap);
        Claims claims = jwtTokenUtils.parseToken(xinP);
        System.out.println(xinP);
        System.out.println(claims.get("userId"));
    }
}

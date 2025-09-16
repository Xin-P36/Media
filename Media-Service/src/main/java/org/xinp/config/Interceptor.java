package org.xinp.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.xinp.entity.UserSettings;
import org.xinp.mapper.UserSettingsMapper;
import org.xinp.util.CurrentHolderUtils;
import org.xinp.util.JwtTokenUtils;

@Component//注册为拦截器Bean
@RequiredArgsConstructor
public class Interceptor implements HandlerInterceptor {
    private final JwtTokenUtils jwtTokenUtils;
    private final UserSettingsMapper userSettingsMapper;
    //在请求处理之前进行调用
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取请求的Token
        String token = request.getHeader("token");
        if(token == null || token.isEmpty()){
            response.setStatus(401);
            return false;
        }
        //校验 Token
        String userId = null;
        try {
            userId = jwtTokenUtils.parseToken(token).get("userId").toString();
        }catch (Exception e){
            response.setStatus(401);
            return false;
        }
        //数据库查询按Token的有效性
        UserSettings user = userSettingsMapper.selectById(userId);
        if(user == null || !user.getToken().equals(token)){
            response.setStatus(401);
            return false;
        }
        //将用户信息ID存入ThreadLocal中
        CurrentHolderUtils.setCurrentUser(userId);
        //验证通过
        return true;
    }
    //请求处理之后进行调用，但是在视图被渲染之前
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        //HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
        //清理线程变量
        CurrentHolderUtils.clear();
    }
}
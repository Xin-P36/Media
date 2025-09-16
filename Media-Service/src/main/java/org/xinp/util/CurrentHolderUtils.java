package org.xinp.util;

public class CurrentHolderUtils {
    private static ThreadLocal<String> currentUser = new ThreadLocal<>();
    /**
     * 设置当前用户ID
     * @param userId 用户ID
     */
    public static void setCurrentUser(String userId)
    {
        currentUser.set(userId);
    }
    /**
     * 获取当前用户ID
     * @return 当前用户ID
     */
    public static String getCurrentUser()
    {
        return currentUser.get();
    }
    /**
     * 删除该线程的局部变量
     */
    public static void clear()
    {
        currentUser.remove();
    }
}
import axios from "axios";

// 创建axios实例
const request = axios.create({
    // 基础路径
    baseURL: '/api',
    // 超时时间
    timeout: 5000
})

//axios响应拦截器
request.interceptors.response.use(
    (response) => { // 响应成功
        return response.data
    },
    (error) => { // 响应失败
        return Promise.reject(error)
    }
)
//axios请求拦截器
request.interceptors.request.use(
    (config) => { //成功回调
        //在请求时添加token
        const user = JSON.parse(localStorage.getItem('userToken'));
        if(user && user.token){ //判断用户是否登录
            config.headers.token = user.token;//设置请求头
        }
        return config //这里直接返回config，服务器的数据
    },
    (error) => { //失败回调
        if(error.response && error.response.status === 401){
            ElMessage.error('登录信息已过期，请重新登录');
            // 注意：这里需要导入 router 或使用其他方式跳转
        }
        return Promise.reject(error) //错误回调，返回错误信息
    }
)

// 导出 axios 实例
export default request
<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";

// 初始化
onMounted(() => {
  //初始用户数据
  const userToken = ref(
    {
        "avatar": "/BackgroundImg/avatar.jpg",
        "loginBackground": "/BackgroundImg/login.png",
        "homeBackground": "/BackgroundImg/home.png",
        "token": null
    }
  );
  if(!JSON.parse(localStorage.getItem("userToken"))){ //判断本地存储中是否有用户数据，没有初始化
    localStorage.setItem("userToken", JSON.stringify(userToken.value)); //初始化用户数据
    
  }
  if(JSON.parse(localStorage.getItem("userToken")).token == null){ //判断用户是否登录
    useRouter().push("/login"); //跳转到登录页面
  }
});

//设置默认参数
</script>

<template>
  <!-- 显示区域 -->
  <router-view></router-view>
</template>

<style >
/* 全局样式让html与div占满整个页面 */
html,
body {
  margin: 0;
  padding: 0;
  width: 100%;
  height: 100%;
}
</style>
<script setup lang="ts">
import { reactive, ref, watch,onMounted } from "vue";
import type { FormRules, FormInstance } from "element-plus";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import request from "@/utils/axiosRequest";

const router = useRouter();

//初始化
//背景图片地址
let backgroundLogin = ref("");
//初始化数据
onMounted(() => { 
    //获取背景图片
    const userToken = localStorage.getItem("userToken");
    if (userToken) {
        try {
            backgroundLogin.value = JSON.parse(userToken).loginBackground;
        } catch (e) {
            console.error("解析 userToken 失败:", e);
        }
    }
});


//表单数据
const ruleForm = ref({
  name: "",
  password: "",
});
// 定义响应数据类型
interface LoginResponse {
  code: number;
  message: string;
  data: {
    account: string;
    token: string;
    nickName: string;
    avatar: string | null;
    thumbnailThreshold: number | null;
    width: number | null;
    height: number | null;
    loginBackground: string | null;
    homeBackground: string | null;
  };
}
//定义提交按钮是否启用
const submitFormButton = ref(true);
// 校验需要在from上添加属性
const formRef = ref<FormInstance>();
//校验规则
const rules = reactive<FormRules<typeof ruleForm>>({
  name: [
    //required：是否为必填，message：校验失败提示，trigger：触发方式失焦
    { required: true, message: "请输入用户名", trigger: "blur" },
    //min：最小长度，max：最大长度
    { min: 2, max: 10, message: "长度在2-10个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 4, max: 20, message: "长度在4-20个字符", trigger: "blur" },
  ],
});

//定义函数
//监控表单，决定提交按钮是否启用
watch(
  ruleForm,
  () => {
    submitFormButton.value = !(
      ruleForm.value.name.length >= 2 &&
      ruleForm.value.name.length <= 10 &&
      ruleForm.value.password.length >= 4 &&
      ruleForm.value.password.length <= 20
    );
  },
  { deep: true }
);
//提交表单
const submitForm = async () => {
  // 表单校验
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid, fields) => {
    if (valid) {
      try {
        // 发送登录请求
        // 注意：GET 请求通常不携带请求体，但根据用户要求，我们尝试在请求体中提交账号和密码
        const responseData: LoginResponse = await request({
          url: '/user/login',
          method: 'POST',
          data: {
            account: ruleForm.value.name,
            password: ruleForm.value.password
          }
        });
        
        // 检查响应状态
        if (responseData.code === 200) {
          // 登录成功提示
          ElMessage({
            message: '登录成功',
            type: 'success'
          });
          
          // 将返回的数据存入 localStorage
          localStorage.setItem("userToken", JSON.stringify(responseData.data));
          
          // 登录成功跳转到 home.vue 页面
          router.push({ name: "home" });
        } else {
          // 处理登录失败的情况
          console.error("登录失败:", responseData.message);
          // 添加错误提示
          ElMessage({
            message: '登录失败: ' + responseData.message,
            type: 'error'
          });
        }
      } catch (error) {
        // 处理网络错误或其他异常
        console.error("登录请求失败:", error);
        // 错误提示
        ElMessage({
          message: '登录请求失败，请检查网络连接',
          type: 'error'
        });
      }
    } else {
      console.log('表单校验失败', fields);
    }
  });
};
//清空表单
const resetForm = () => {
  //清空校验
  formRef.value.resetFields();
  //清空表单
  ruleForm.value = {
    name: "",
    password: "",
  };
};
</script>   

<template>
  <div id="background-login" :style="{ backgroundImage: 'url(' + backgroundLogin + ')' }">
    <div id="login">
      <h1 id="logintitle">你好 ʕ´• ᴥ•̥`ʔ ！</h1>
      <!-- model: 表单数据对象 ，rules: 表单验证规则，ref：绑定表单对象formRef用于获取表单对象（清除校验）-->
      <el-form
        ref="formRef"
        style="max-width: 80%"
        :model="ruleForm"
        status-icon
        :rules="rules"
        label-width="auto"
        @keyup.enter="submitForm"
      >
        <!-- label：输入框前面显示什么，prop：校验规则绑定 -->
        <el-form-item label="用户名" prop="name">
          <el-input
            v-model="ruleForm.name"
            type="text"
            placeholder="账号 2~10"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="ruleForm.password"
            type="password"
            placeholder="密码 6~20"
          />
        </el-form-item>

        <el-form-item>
          <div id="login-btn">
            <el-button @click="resetForm">重置</el-button>
            <el-button
              type="primary"
              @click="submitForm"
              :disabled="submitFormButton"
              >登录</el-button
            >
          </div>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
/* 设置背景图片 */
#background-login {
  /* 占满整个视口 */
  width: 100vw;
  height: 100vh;
  /* 背景图片设置 */
  background-size: cover; /* 关键：铺满全屏不拉伸变形 */
  background-position: center; /* 居中显示 */
  background-repeat: no-repeat; /* 不重复 */
  display: flex; /* 登录框居中 */
  justify-content: center;
  align-items: center;
}

/* 设置登录表单 */
#login {
  width: 30%; /* 设置组件的宽度为30% */
  border-radius: 10px; /* 边框圆角控制 */
  box-shadow: var(--el-box-shadow-dark); /* 阴影样式 */
  /* 组件上下左右居中*/
  display: flex;
  flex-direction: column;
  align-items: center;
  margin: auto; /* 居中 */
  margin-top: 10%; /* 距离顶部10% */
  background: rgba(255, 255, 255, 0.1); /* 半透明背景增强可读性 */
  backdrop-filter: blur(5px); /* 毛玻璃效果 */
}

/* 设置标题 */
#logintitle {
  margin: 20px 0; /* 距离上下边距10px */
}

/* 设置登录按钮 */
#login-btn {
  width: 100%;
  display: flex; /* 水平居中 */
  justify-content: space-between;
  padding: 0 5%; /* 距离左右边距5% */
  box-sizing: border-box;
}
</style>

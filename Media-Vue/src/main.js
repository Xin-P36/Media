import { createApp } from 'vue' //创建app
import App from './App.vue' //组件
import router from './router' //路由

import ElementOlus from "element-plus" //element-plus
import "element-plus/dist/index.css" //element-plus样式
import * as ElementPlusIconsVue from '@element-plus/icons-vue' //element-plus图标

import VueAxios from 'vue-axios' //axios
import axios from 'axios' //axios

import masonry from 'vue-next-masonry' //图片瀑布流插件

const app = createApp(App) //创建app

app.use(router) //使用路由
app.use(ElementOlus) //使用element-plus
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
  }
app.use(VueAxios, axios) //使用axios
app.use(masonry) //使用图片瀑布流插件

app.mount('#app') //挂载app

import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/login.vue"),
    },
    {
      path: "/",
      name: "home",
      component: () => import("@/views/home.vue"),
      redirect:"/show", //默认进入show页面
      children:[
        {
          path:"/show",
          name:"show",
          component:()=>import("@/views/show.vue")
        },
        {
          path:"/finishing",
          name:"finishing",
          component:()=>import("@/views/finishing.vue")
        }
      ]
    },
    {
      path:"/pigeonhole",
      name:"pigeonhole",
      component:()=>import("@/views/pigeonhole.vue")
    },
    {
      path:"/classificationdisplay",
      name:"classificationdisplay",
      component:()=>import("@/views/classificationdisplay.vue")
    },
  ],
})

export default router

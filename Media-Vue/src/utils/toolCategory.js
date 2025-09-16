import request from './axiosRequest'

/**
 * 获取工具分类树状结构
 * @returns {Promise} 返回分类树状结构数据的Promise
 */
export const getToolCategoryTree = () => {
  return request({
    url: '/tool/tree',
    method: 'GET'
  })
}

export default {
  getToolCategoryTree
}
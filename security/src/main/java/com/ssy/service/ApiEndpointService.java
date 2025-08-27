package com.ssy.service;

import com.ssy.entity.ApiEndpointEntity;

import java.util.List;

/**
 * API接口管理服务接口
 * 提供API接口的扫描、存储和查询功能
 * 
 * @author Zhang San
 * @version 1.0
 * @date 2025/1/27
 * @email 3278440884@qq.com
 */
public interface ApiEndpointService {

    /**
     * 扫描并保存所有API接口
     * 在应用启动时调用，自动扫描所有Controller并保存到数据库
     * 
     * @return 扫描到的接口数量
     */
    int scanAndSaveAllEndpoints();

    /**
     * 分页查询API接口
     * 
     * @param page        页码（从1开始）
     * @param size        每页大小
     * @param keyword     搜索关键词
     * @param moduleGroup 模块分组
     * @return 分页结果
     */
    PageResult<ApiEndpointEntity> getEndpointsByPage(int page, int size, String keyword, String moduleGroup);

    /**
     * 获取所有模块分组
     * 
     * @return 模块分组列表
     */
    List<String> getAllModuleGroups();

    /**
     * 根据ID查询API接口
     * 
     * @param id 接口ID
     * @return API接口实体
     */
    ApiEndpointEntity getEndpointById(Long id);

    /**
     * 更新API接口信息
     * 
     * @param apiEndpoint API接口实体
     * @return 是否成功
     */
    boolean updateEndpoint(ApiEndpointEntity apiEndpoint);

    /**
     * 强制重新扫描所有接口
     * 删除现有数据，重新扫描并保存
     * 
     * @return 扫描到的接口数量
     */
    int forceRescanAllEndpoints();

    /**
     * 增量扫描新接口
     * 只添加新发现的接口，不删除已存在的
     * 
     * @return 新增的接口数量
     */
    int incrementalScanEndpoints();

    /**
     * 获取所有接口
     * 
     * @return 接口列表
     */
    List<ApiEndpointEntity> getAllEndpoints();

    /**
     * 分页结果包装类
     */
    class PageResult<T> {
        private List<T> records;
        private long total;
        private int page;
        private int size;
        private int totalPages;

        public PageResult(List<T> records, long total, int page, int size) {
            this.records = records;
            this.total = total;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil((double) total / size);
        }

        // Getters and Setters
        public List<T> getRecords() {
            return records;
        }

        public void setRecords(List<T> records) {
            this.records = records;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
    }
}

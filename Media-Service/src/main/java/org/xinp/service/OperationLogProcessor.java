package org.xinp.service;

import org.xinp.entity.OperationLogs;

@FunctionalInterface
public interface OperationLogProcessor {
    /**
     * 执行一个具体的操作日志任务。
     * @param log 要执行的操作日志记录
     * @throws Exception 如果执行失败，抛出异常
     */
    void process(OperationLogs log) throws Exception;
}
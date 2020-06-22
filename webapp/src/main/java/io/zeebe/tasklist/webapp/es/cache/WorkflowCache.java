/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.webapp.es.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;

@Component
public class WorkflowCache {
  
  private static final Logger logger = LoggerFactory.getLogger(WorkflowCache.class);

  private Map<String, WorkflowCacheEntity> cache = new ConcurrentHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;

  @Autowired
  private WorkflowReader workflowReader;

  private WorkflowCacheEntity getWorkflowCacheEntity(String workflowId) {
    if (cache.get(workflowId) == null) {
      final Optional<WorkflowEntity> workflowMaybe = readWorkflowByKey(workflowId);
      if (workflowMaybe.isPresent()) {
        WorkflowEntity workflow = workflowMaybe.get();
        putToCache(workflowId, workflow);
      }
    }
    return cache.get(workflowId);
  }

  public String getWorkflowName(String workflowId) {
    final WorkflowCacheEntity cachedWorkflowData = getWorkflowCacheEntity(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getName();
    } else {
      return null;
    }
  }

  public String getTaskName(String workflowId, String flowNodeId) {
    final WorkflowCacheEntity cachedWorkflowData = getWorkflowCacheEntity(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getFlowNodeNames().get(flowNodeId);
    } else {
      return null;
    }
  }
  
  private Optional<WorkflowEntity> readWorkflowByKey(String workflowId) {
    try {
      return Optional.of(workflowReader.getWorkflow(workflowId));
    } catch (TasklistRuntimeException ex) {
      return Optional.empty();
    }
  }

  public void putToCache(String workflowId, WorkflowEntity workflow) {
    if (cache.size() >= CACHE_MAX_SIZE) {
      // remove 1st element
      final Iterator<String> iterator = cache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
    cache.put(workflowId, WorkflowCacheEntity.createFrom(workflow));
  }

  public void clearCache() {
    cache.clear();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.zeebeimport.ElasticsearchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import static org.camunda.operate.util.ThreadUtil.sleepFor;

@Component
public class WorkflowCache {
  
  private static final Logger logger = LoggerFactory.getLogger(WorkflowCache.class);

  private Map<Long, WorkflowEntity> cache = new ConcurrentHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;
  private static final int MAX_ATTEMPTS = 5;
  private static final long WAIT_TIME = 200;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  public String getWorkflowNameOrDefaultValue(Long workflowKey, String defaultValue) {
    final WorkflowEntity cachedWorkflowData = cache.get(workflowKey);
    String workflowName = defaultValue;
    if (cachedWorkflowData != null) {
      workflowName = cachedWorkflowData.getName();    
    } else {
      final Optional<WorkflowEntity> workflowMaybe = findOrWaitWorkflow(workflowKey, MAX_ATTEMPTS, WAIT_TIME);
      if (workflowMaybe.isPresent()) {
        WorkflowEntity workflow = workflowMaybe.get();
        putToCache(workflowKey, workflow);
        workflowName = workflow.getName();
      }
    }
    if(StringUtils.isEmpty(workflowName)) {
      logger.debug("WorkflowName is empty, use default value: {} ",defaultValue);
      workflowName = defaultValue;
    }
    return workflowName;
  }
  
  private Optional<WorkflowEntity> readWorkflowByKey(Long workflowKey) {
    try {
      return Optional.of(elasticsearchManager.getWorkflow(workflowKey));
    } catch (OperateRuntimeException ex) {
      return Optional.empty();
    }
  }

  public Optional<WorkflowEntity> findOrWaitWorkflow(Long workflowKey, int attempts, long sleepInMilliseconds) {
    int attemptsCount = 0;
    Optional<WorkflowEntity> foundWorkflow = Optional.empty();
    while (!foundWorkflow.isPresent() && attemptsCount < attempts) {
      attemptsCount++;
      foundWorkflow = readWorkflowByKey(workflowKey);
      if (!foundWorkflow.isPresent()) {
        logger.debug("{} attempts left. Waiting {} ms.", attempts - attemptsCount, sleepInMilliseconds);
        sleepFor(sleepInMilliseconds);
      } else {
        logger.debug("Found workflow after {} attempts. Waited {} ms.", attemptsCount, (attemptsCount - 1) * sleepInMilliseconds);
      }
    }
    return foundWorkflow;
  }

  public void putToCache(Long workflowKey, WorkflowEntity workflow) {
    if (cache.size() >= CACHE_MAX_SIZE) {
      // remove 1st element
      final Iterator<Long> iterator = cache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
    cache.put(workflowKey, workflow);
  }

  public void clearCache() {
    cache.clear();
  }
}

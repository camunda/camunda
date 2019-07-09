/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WorkflowCache {
  
  private static final Logger logger = LoggerFactory.getLogger(WorkflowCache.class);

  private LinkedHashMap<Long, WorkflowEntity> cache = new LinkedHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;
  private static final int MAX_ATTEMPTS = 5;
  private static final long WAIT_TIME = 200;

  @Autowired
  private WorkflowReader workflowReader;

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
      return Optional.of(workflowReader.getWorkflow(workflowKey));
    } catch (NotFoundException nfe) {
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
        try {
          Thread.sleep(sleepInMilliseconds);
        } catch (InterruptedException e) {
          logger.debug(e.getMessage());
        }
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

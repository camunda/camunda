/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;

import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.es.reader.WorkflowReader;
import org.camunda.operate.rest.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowCache {
  
  private static final Logger logger = LoggerFactory.getLogger(WorkflowCache.class);

  private LinkedHashMap<String, WorkflowEntity> cache = new LinkedHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;

  @Autowired
  private WorkflowReader workflowReader;

  public String getWorkflowName(String workflowId) {
    final WorkflowEntity cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getName();
    } else {
      final WorkflowEntity newValue = findWorkflow(workflowId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue.getName();
      } else {
        return null;
      }
    }
  }

  public Integer getWorkflowVersion(String workflowId) {
    final WorkflowEntity cachedWorkflowData = cache.get(workflowId);
    if (cachedWorkflowData != null) {
      return cachedWorkflowData.getVersion();
    } else {
      final WorkflowEntity newValue = findWorkflow(workflowId);
      if (newValue != null) {
        putToCache(workflowId, newValue);
        return newValue.getVersion();
      } else {
        return null;
      }
    }
  }

  private WorkflowEntity findWorkflow(String workflowId) {
    try {
      return workflowReader.getWorkflow(workflowId);
    } catch (NotFoundException nfe) {
      logger.debug(String.format("Workflow with id %s not found", workflowId),nfe);
      return null;
    }
  }

  public void putToCache(String workflowId, WorkflowEntity workflow) {
    if (cache.size() >= CACHE_MAX_SIZE) {
      //remove 1st element
      final Iterator<String> iterator = cache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
    cache.put(workflowId, workflow);
  }

  public void clearCache() {
    cache.clear();
  }

}

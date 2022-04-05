/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es.cache;

import io.camunda.tasklist.entities.ProcessEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCache.class);
  private static final int CACHE_MAX_SIZE = 100;
  private Map<String, ProcessCacheEntity> cache = new ConcurrentHashMap<>();
  @Autowired private ProcessReader processReader;

  private ProcessCacheEntity getProcessCacheEntity(String processId) {
    if (cache.get(processId) == null) {
      final Optional<ProcessEntity> processMaybe = readProcessByKey(processId);
      if (processMaybe.isPresent()) {
        final ProcessEntity process = processMaybe.get();
        putToCache(processId, process);
      }
    }
    return cache.get(processId);
  }

  public String getProcessName(String processId) {
    final ProcessCacheEntity cachedProcessData = getProcessCacheEntity(processId);
    if (cachedProcessData != null) {
      return cachedProcessData.getName();
    } else {
      return null;
    }
  }

  public String getTaskName(String processId, String flowNodeBpmnId) {
    final ProcessCacheEntity cachedProcessData = getProcessCacheEntity(processId);
    if (cachedProcessData != null) {
      return cachedProcessData.getFlowNodeNames().get(flowNodeBpmnId);
    } else {
      return null;
    }
  }

  private Optional<ProcessEntity> readProcessByKey(String processId) {
    try {
      return Optional.of(processReader.getProcess(processId));
    } catch (TasklistRuntimeException ex) {
      return Optional.empty();
    }
  }

  public void putToCache(String processId, ProcessEntity process) {
    if (cache.size() >= CACHE_MAX_SIZE) {
      // remove 1st element
      final Iterator<String> iterator = cache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
    cache.put(processId, ProcessCacheEntity.createFrom(process));
  }

  public void clearCache() {
    cache.clear();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.cache;

import io.camunda.spring.utils.ConditionalOnRdbmsDisabled;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnRdbmsDisabled
public class ProcessCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCache.class);
  private static final int CACHE_MAX_SIZE = 100;
  private final Map<String, ProcessCacheEntity> cache = new ConcurrentHashMap<>();
  @Autowired private ProcessStore processStore;

  private ProcessCacheEntity getProcessCacheEntity(final String processId) {
    if (cache.get(processId) == null) {
      final Optional<ProcessEntity> processMaybe = readProcessByKey(processId);
      if (processMaybe.isPresent()) {
        final ProcessEntity process = processMaybe.get();
        putToCache(processId, process);
      }
    }
    return cache.get(processId);
  }

  public String getProcessName(final String processId) {
    final ProcessCacheEntity cachedProcessData = getProcessCacheEntity(processId);
    if (cachedProcessData != null) {
      return cachedProcessData.getName();
    } else {
      return null;
    }
  }

  public String getTaskName(final String processId, final String flowNodeBpmnId) {
    final ProcessCacheEntity cachedProcessData = getProcessCacheEntity(processId);
    if (cachedProcessData != null) {
      return cachedProcessData.getFlowNodeNames().get(flowNodeBpmnId);
    } else {
      return null;
    }
  }

  private Optional<ProcessEntity> readProcessByKey(final String processId) {
    try {
      return Optional.of(processStore.getProcess(processId));
    } catch (final TasklistRuntimeException ex) {
      return Optional.empty();
    }
  }

  public void putToCache(final String processId, final ProcessEntity process) {
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.cache;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.entities.ProcessFlowNodeEntity;
import io.camunda.operate.store.ProcessStore;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProcessCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessCache.class);
  private static final int CACHE_MAX_SIZE = 100;
  private static final int MAX_ATTEMPTS = 5;
  private static final long WAIT_TIME = 200;
  private final Map<Long, ProcessEntity> cache = new ConcurrentHashMap<>();
  @Autowired private ProcessStore processStore;

  public String getProcessNameOrDefaultValue(Long processDefinitionKey, String defaultValue) {
    final ProcessEntity cachedProcessData =
        getCachedProcessEntity(processDefinitionKey).orElse(null);
    String processName = defaultValue;
    if (cachedProcessData != null) {
      processName = cachedProcessData.getName();
    }
    if (!StringUtils.hasText(processName)) {
      LOGGER.debug("ProcessName is empty, use default value: {} ", defaultValue);
      processName = defaultValue;
    }
    return processName;
  }

  public String getProcessNameOrBpmnProcessId(Long processDefinitionKey, String defaultValue) {
    final ProcessEntity cachedProcessData =
        getCachedProcessEntity(processDefinitionKey).orElse(null);
    String processName = null;
    if (cachedProcessData != null) {
      processName = cachedProcessData.getName();
      if (processName == null) {
        processName = cachedProcessData.getBpmnProcessId();
      }
    }
    if (!StringUtils.hasText(processName)) {
      LOGGER.debug("ProcessName is empty, use default value: {} ", defaultValue);
      processName = defaultValue;
    }
    return processName;
  }

  public String getFlowNodeNameOrDefaultValue(
      Long processDefinitionKey, String flowNodeId, String defaultValue) {
    final ProcessEntity cachedProcessData =
        getCachedProcessEntity(processDefinitionKey).orElse(null);
    String flowNodeName = defaultValue;
    if (cachedProcessData != null && flowNodeId != null) {
      final ProcessFlowNodeEntity flowNodeEntity =
          cachedProcessData.getFlowNodes().stream()
              .filter(x -> flowNodeId.equals(x.getId()))
              .findFirst()
              .orElse(null);
      if (flowNodeEntity != null) {
        flowNodeName = flowNodeEntity.getName();
      }
    }
    if (!StringUtils.hasText(flowNodeName)) {
      LOGGER.debug("FlowNodeName is empty, use default value: {} ", defaultValue);
      flowNodeName = defaultValue;
    }
    return flowNodeName;
  }

  private Optional<ProcessEntity> getCachedProcessEntity(Long processDefinitionKey) {
    ProcessEntity cachedProcessData = cache.get(processDefinitionKey);
    if (cachedProcessData == null) {
      final Optional<ProcessEntity> processMaybe =
          findOrWaitProcess(processDefinitionKey, MAX_ATTEMPTS, WAIT_TIME);
      if (processMaybe.isPresent()) {
        cachedProcessData = processMaybe.get();
        putToCache(processDefinitionKey, cachedProcessData);
      }
    }
    return Optional.ofNullable(cachedProcessData);
  }

  private Optional<ProcessEntity> readProcessByKey(Long processDefinitionKey) {
    try {
      return Optional.of(processStore.getProcessByKey(processDefinitionKey));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  public Optional<ProcessEntity> findOrWaitProcess(
      Long processDefinitionKey, int attempts, long sleepInMilliseconds) {
    int attemptsCount = 0;
    Optional<ProcessEntity> foundProcess = Optional.empty();
    while (foundProcess.isEmpty() && attemptsCount < attempts) {
      attemptsCount++;
      foundProcess = readProcessByKey(processDefinitionKey);
      if (foundProcess.isEmpty()) {
        LOGGER.debug(
            "Unable to find process {}. {} attempts left. Waiting {} ms.",
            processDefinitionKey,
            attempts - attemptsCount,
            sleepInMilliseconds);
        sleepFor(sleepInMilliseconds);
      } else {
        LOGGER.debug(
            "Found process {} after {} attempts. Waited {} ms.",
            processDefinitionKey,
            attemptsCount,
            (attemptsCount - 1) * sleepInMilliseconds);
      }
    }
    return foundProcess;
  }

  public void putToCache(Long processDefinitionKey, ProcessEntity process) {
    if (cache.size() >= CACHE_MAX_SIZE) {
      // remove 1st element
      final Iterator<Long> iterator = cache.keySet().iterator();
      if (iterator.hasNext()) {
        iterator.next();
        iterator.remove();
      }
    }
    cache.put(processDefinitionKey, process);
  }

  public void clearCache() {
    cache.clear();
  }
}

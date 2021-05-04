/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.cache;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.zeebeimport.ElasticsearchManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

@Component
public class ProcessCache {
  
  private static final Logger logger = LoggerFactory.getLogger(ProcessCache.class);

  private Map<Long, ProcessEntity> cache = new ConcurrentHashMap<>();

  private static final int CACHE_MAX_SIZE = 100;
  private static final int MAX_ATTEMPTS = 5;
  private static final long WAIT_TIME = 200;

  @Autowired
  private ElasticsearchManager elasticsearchManager;

  public String getProcessNameOrDefaultValue(Long processDefinitionKey, String defaultValue) {
    final ProcessEntity cachedProcessData = cache.get(processDefinitionKey);
    String processName = defaultValue;
    if (cachedProcessData != null) {
      processName = cachedProcessData.getName();
    } else {
      final Optional<ProcessEntity> processMaybe = findOrWaitProcess(processDefinitionKey, MAX_ATTEMPTS, WAIT_TIME);
      if (processMaybe.isPresent()) {
        ProcessEntity process = processMaybe.get();
        putToCache(processDefinitionKey, process);
        processName = process.getName();
      }
    }
    if(StringUtils.isEmpty(processName)) {
      logger.debug("ProcessName is empty, use default value: {} ",defaultValue);
      processName = defaultValue;
    }
    return processName;
  }
  
  private Optional<ProcessEntity> readProcessByKey(Long processDefinitionKey) {
    try {
      return Optional.of(elasticsearchManager.getProcess(processDefinitionKey));
    } catch (OperateRuntimeException ex) {
      return Optional.empty();
    }
  }

  public Optional<ProcessEntity> findOrWaitProcess(Long processDefinitionKey, int attempts, long sleepInMilliseconds) {
    int attemptsCount = 0;
    Optional<ProcessEntity> foundProcess = Optional.empty();
    while (!foundProcess.isPresent() && attemptsCount < attempts) {
      attemptsCount++;
      foundProcess = readProcessByKey(processDefinitionKey);
      if (!foundProcess.isPresent()) {
        logger.debug("Unable to find process {}. {} attempts left. Waiting {} ms.", processDefinitionKey, attempts - attemptsCount, sleepInMilliseconds);
        sleepFor(sleepInMilliseconds);
      } else {
        logger.debug("Found process {} after {} attempts. Waited {} ms.", processDefinitionKey, attemptsCount, (attemptsCount - 1) * sleepInMilliseconds);
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

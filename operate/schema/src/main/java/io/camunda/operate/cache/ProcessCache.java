/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

  private static final Logger logger = LoggerFactory.getLogger(ProcessCache.class);
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
      logger.debug("ProcessName is empty, use default value: {} ", defaultValue);
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
      logger.debug("ProcessName is empty, use default value: {} ", defaultValue);
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
      ProcessFlowNodeEntity flowNodeEntity =
          cachedProcessData.getFlowNodes().stream()
              .filter(x -> flowNodeId.equals(x.getId()))
              .findFirst()
              .orElse(null);
      if (flowNodeEntity != null) {
        flowNodeName = flowNodeEntity.getName();
      }
    }
    if (!StringUtils.hasText(flowNodeName)) {
      logger.debug("FlowNodeName is empty, use default value: {} ", defaultValue);
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
        logger.debug(
            "Unable to find process {}. {} attempts left. Waiting {} ms.",
            processDefinitionKey,
            attempts - attemptsCount,
            sleepInMilliseconds);
        sleepFor(sleepInMilliseconds);
      } else {
        logger.debug(
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

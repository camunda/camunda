/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.CorrelatedMessage;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.CorrelatedMessageResult;
import java.util.Objects;

public class CorrelatedMessageImpl implements CorrelatedMessage {

  private final String correlationKey;
  private final String correlationTime;
  private final String elementId;
  private final Long elementInstanceKey;
  private final Long messageKey;
  private final String messageName;
  private final Integer partitionId;
  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long subscriptionKey;
  private final String tenantId;

  public CorrelatedMessageImpl(final CorrelatedMessageResult item) {
    correlationKey = item.getCorrelationKey();
    correlationTime = item.getCorrelationTime();
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    messageKey = ParseUtil.parseLongOrNull(item.getMessageKey());
    messageName = item.getMessageName();
    partitionId = item.getPartitionId();
    processDefinitionId = item.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    subscriptionKey = ParseUtil.parseLongOrNull(item.getSubscriptionKey());
    tenantId = item.getTenantId();
  }

  @Override
  public String getCorrelationKey() {
    return correlationKey;
  }

  @Override
  public String getCorrelationTime() {
    return correlationTime;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public Long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public Long getMessageKey() {
    return messageKey;
  }

  @Override
  public String getMessageName() {
    return messageName;
  }

  @Override
  public Integer getPartitionId() {
    return partitionId;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public Long getSubscriptionKey() {
    return subscriptionKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        correlationKey,
        correlationTime,
        elementId,
        elementInstanceKey,
        messageKey,
        messageName,
        partitionId,
        processDefinitionId,
        processDefinitionKey,
        processInstanceKey,
        subscriptionKey,
        tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CorrelatedMessageImpl correlatedMessage = (CorrelatedMessageImpl) o;
    return Objects.equals(correlationKey, correlatedMessage.correlationKey)
        && Objects.equals(correlationTime, correlatedMessage.correlationTime)
        && Objects.equals(elementId, correlatedMessage.elementId)
        && Objects.equals(elementInstanceKey, correlatedMessage.elementInstanceKey)
        && Objects.equals(messageKey, correlatedMessage.messageKey)
        && Objects.equals(messageName, correlatedMessage.messageName)
        && Objects.equals(partitionId, correlatedMessage.partitionId)
        && Objects.equals(processDefinitionId, correlatedMessage.processDefinitionId)
        && Objects.equals(processDefinitionKey, correlatedMessage.processDefinitionKey)
        && Objects.equals(processInstanceKey, correlatedMessage.processInstanceKey)
        && Objects.equals(subscriptionKey, correlatedMessage.subscriptionKey)
        && Objects.equals(tenantId, correlatedMessage.tenantId);
  }
}
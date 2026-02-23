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

import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.response.MessageSubscription;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.impl.util.ParseUtil;
import io.camunda.client.protocol.rest.MessageSubscriptionResult;
import java.time.OffsetDateTime;
import java.util.Objects;

public class MessageSubscriptionImpl implements MessageSubscription {

  private final Long messageSubscriptionKey;
  private final String processDefinitionId;
  private final Long processDefinitionKey;
  private final Long processInstanceKey;
  private final Long rootProcessInstanceKey;
  private final String elementId;
  private final Long elementInstanceKey;
  private final MessageSubscriptionState messageSubscriptionState;
  private final OffsetDateTime lastUpdatedDate;
  private final String messageName;
  private final String correlationKey;
  private final String tenantId;

  public MessageSubscriptionImpl(final MessageSubscriptionResult item) {
    messageSubscriptionKey = ParseUtil.parseLongOrNull(item.getMessageSubscriptionKey());
    processDefinitionId = item.getProcessDefinitionId();
    processDefinitionKey = ParseUtil.parseLongOrNull(item.getProcessDefinitionKey());
    processInstanceKey = ParseUtil.parseLongOrNull(item.getProcessInstanceKey());
    rootProcessInstanceKey = ParseUtil.parseLongOrNull(item.getRootProcessInstanceKey());
    elementId = item.getElementId();
    elementInstanceKey = ParseUtil.parseLongOrNull(item.getElementInstanceKey());
    messageSubscriptionState =
        EnumUtil.convert(item.getMessageSubscriptionState(), MessageSubscriptionState.class);
    lastUpdatedDate = ParseUtil.parseOffsetDateTimeOrNull(item.getLastUpdatedDate());
    messageName = item.getMessageName();
    correlationKey = item.getCorrelationKey();
    tenantId = item.getTenantId();
  }

  @Override
  public Long getMessageSubscriptionKey() {
    return messageSubscriptionKey;
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
  public Long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
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
  public MessageSubscriptionState getMessageSubscriptionState() {
    return messageSubscriptionState;
  }

  @Override
  public OffsetDateTime getLastUpdatedDate() {
    return lastUpdatedDate;
  }

  @Override
  public String getMessageName() {
    return messageName;
  }

  @Override
  public String getCorrelationKey() {
    return correlationKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        messageSubscriptionKey,
        processDefinitionId,
        processDefinitionKey,
        processInstanceKey,
        rootProcessInstanceKey,
        elementId,
        elementInstanceKey,
        messageSubscriptionState,
        lastUpdatedDate,
        messageName,
        correlationKey,
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
    final MessageSubscriptionImpl subscription = (MessageSubscriptionImpl) o;
    return Objects.equals(messageSubscriptionKey, subscription.messageSubscriptionKey)
        && Objects.equals(processDefinitionId, subscription.processDefinitionId)
        && Objects.equals(processDefinitionKey, subscription.processDefinitionKey)
        && Objects.equals(processInstanceKey, subscription.processInstanceKey)
        && Objects.equals(rootProcessInstanceKey, subscription.rootProcessInstanceKey)
        && Objects.equals(elementId, subscription.elementId)
        && Objects.equals(elementInstanceKey, subscription.elementInstanceKey)
        && messageSubscriptionState == subscription.messageSubscriptionState
        && Objects.equals(lastUpdatedDate, subscription.lastUpdatedDate)
        && Objects.equals(messageName, subscription.messageName)
        && Objects.equals(correlationKey, subscription.correlationKey)
        && Objects.equals(tenantId, subscription.tenantId);
  }
}

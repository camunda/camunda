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
package io.camunda.process.test.utils;

import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.response.MessageSubscription;
import java.time.OffsetDateTime;

public class MessageSubscriptionBuilder implements MessageSubscription {

  private Long messageSubscriptionKey;
  private String processDefinitionId;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long rootProcessInstanceKey;
  private String elementId;
  private Long elementInstanceKey;
  private OffsetDateTime lastUpdatedDate;
  private String messageName;
  private String correlationKey;
  private String tenantId;
  private MessageSubscriptionState messageSubscriptionState;

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

  public MessageSubscriptionBuilder setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public MessageSubscriptionBuilder setCorrelationKey(final String correlationKey) {
    this.correlationKey = correlationKey;
    return this;
  }

  public MessageSubscriptionBuilder setMessageName(final String messageName) {
    this.messageName = messageName;
    return this;
  }

  public MessageSubscriptionBuilder setLastUpdatedDate(final OffsetDateTime lastUpdatedDate) {
    this.lastUpdatedDate = lastUpdatedDate;
    return this;
  }

  public MessageSubscriptionBuilder setMessageSubscriptionState(
      final MessageSubscriptionState messageSubscriptionState) {
    this.messageSubscriptionState = messageSubscriptionState;
    return this;
  }

  public MessageSubscriptionBuilder setElementInstanceKey(final Long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public MessageSubscriptionBuilder setElementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public MessageSubscriptionBuilder setRootProcessInstanceKey(final Long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public MessageSubscriptionBuilder setProcessInstanceKey(final Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
    return this;
  }

  public MessageSubscriptionBuilder setProcessDefinitionKey(final Long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  public MessageSubscriptionBuilder setProcessDefinitionId(final String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
    return this;
  }

  public MessageSubscriptionBuilder setMessageSubscriptionKey(final Long messageSubscriptionKey) {
    this.messageSubscriptionKey = messageSubscriptionKey;
    return this;
  }

  public MessageSubscription build() {
    return this;
  }

  public static MessageSubscriptionBuilder newActiveMessageSubscription(
      final String messageName, final String correlationKey) {
    final MessageSubscriptionBuilder builder = new MessageSubscriptionBuilder();
    builder.setMessageName(messageName);
    builder.setCorrelationKey(correlationKey);
    builder.setMessageSubscriptionState(MessageSubscriptionState.CREATED);
    return builder;
  }
}

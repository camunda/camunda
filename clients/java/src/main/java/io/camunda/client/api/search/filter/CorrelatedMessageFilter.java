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
package io.camunda.client.api.search.filter;

import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface CorrelatedMessageFilter extends SearchRequestFilter {

  /**
   * Filter by correlation key.
   *
   * @param correlationKey the correlation key of the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter correlationKey(String correlationKey);

  /**
   * Filter by correlation key using a {@link StringProperty} consumer.
   *
   * @param fn the correlation key {@link StringProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter correlationKey(Consumer<StringProperty> fn);

  /**
   * Filter by correlation time.
   *
   * @param correlationTime the correlation time of the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter correlationTime(OffsetDateTime correlationTime);

  /**
   * Filter by correlation time using a {@link DateTimeProperty} consumer.
   *
   * @param fn the correlation time {@link DateTimeProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter correlationTime(Consumer<DateTimeProperty> fn);

  /**
   * Filter by element ID.
   *
   * @param elementId the ID of the element
   * @return the updated filter
   */
  CorrelatedMessageFilter elementId(String elementId);

  /**
   * Filter by element ID using a {@link StringProperty} consumer.
   *
   * @param fn the element ID {@link StringProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter elementId(Consumer<StringProperty> fn);

  /**
   * Filter by element instance key.
   *
   * @param elementInstanceKey the key of the element instance
   * @return the updated filter
   */
  CorrelatedMessageFilter elementInstanceKey(Long elementInstanceKey);

  /**
   * Filter by element instance key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the element instance key {@link BasicLongProperty} consumer for the correlated
   *     message
   * @return the updated filter
   */
  CorrelatedMessageFilter elementInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by message key.
   *
   * @param messageKey the key of the message
   * @return the updated filter
   */
  CorrelatedMessageFilter messageKey(Long messageKey);

  /**
   * Filter by message key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the message key {@link BasicLongProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter messageKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by message name.
   *
   * @param messageName the name of the message
   * @return the updated filter
   */
  CorrelatedMessageFilter messageName(String messageName);

  /**
   * Filter by message name using a {@link StringProperty} consumer.
   *
   * @param fn the message name {@link StringProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter messageName(Consumer<StringProperty> fn);

  /**
   * Filter by partition ID.
   *
   * @param partitionId the partition ID
   * @return the updated filter
   */
  CorrelatedMessageFilter partitionId(Integer partitionId);

  /**
   * Filter by partition ID using a {@link IntegerProperty} consumer.
   *
   * @param fn the partition ID {@link IntegerProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter partitionId(Consumer<IntegerProperty> fn);

  /**
   * Filter by process definition ID.
   *
   * @param processDefinitionId the process definition ID
   * @return the updated filter
   */
  CorrelatedMessageFilter processDefinitionId(String processDefinitionId);

  /**
   * Filter by process definition ID using a {@link StringProperty} consumer.
   *
   * @param fn the process definition ID {@link StringProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter processDefinitionId(Consumer<StringProperty> fn);

  /**
   * Filter by process definition key.
   *
   * @param processDefinitionKey the key of the process definition
   * @return the updated filter
   */
  CorrelatedMessageFilter processDefinitionKey(Long processDefinitionKey);

  /**
   * Filter by process definition key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the process definition key {@link BasicLongProperty} consumer for the correlated
   *     message
   * @return the updated filter
   */
  CorrelatedMessageFilter processDefinitionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by process instance key.
   *
   * @param processInstanceKey the key of the process instance
   * @return the updated filter
   */
  CorrelatedMessageFilter processInstanceKey(Long processInstanceKey);

  /**
   * Filter by process instance key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the process instance key {@link BasicLongProperty} consumer for the correlated
   *     message
   * @return the updated filter
   */
  CorrelatedMessageFilter processInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by subscription key.
   *
   * @param subscriptionKey the key of the subscription
   * @return the updated filter
   */
  CorrelatedMessageFilter subscriptionKey(Long subscriptionKey);

  /**
   * Filter by subscription key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the subscription key {@link BasicLongProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter subscriptionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by tenant ID.
   *
   * @param tenantId the tenant ID of the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter tenantId(String tenantId);

  /**
   * Filter by tenant ID using a {@link StringProperty} consumer.
   *
   * @param fn the tenant ID {@link StringProperty} consumer for the correlated message
   * @return the updated filter
   */
  CorrelatedMessageFilter tenantId(Consumer<StringProperty> fn);
}

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

import io.camunda.client.api.search.enums.MessageSubscriptionState;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.MessageSubscriptionStateProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface MessageSubscriptionFilter extends SearchRequestFilter {

  /**
   * Filter by message subscription key.
   *
   * @param messageSubscriptionKey key of the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter messageSubscriptionKey(Long messageSubscriptionKey);

  /**
   * Filter message subscriptions by key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the key {@link BasicLongProperty} consumer for the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter messageSubscriptionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by process definition ID.
   *
   * @param processDefinitionId the process definition ID
   * @return the updated filter
   */
  MessageSubscriptionFilter processDefinitionId(String processDefinitionId);

  /**
   * Filter by process definition ID using a {@link StringProperty} consumer.
   *
   * @param fn the process definition ID {@link StringProperty} consumer for the message
   *     subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter processDefinitionId(Consumer<StringProperty> fn);

  /**
   * Filter by process instance key.
   *
   * @param processInstanceKey the key of the process instance
   * @return the updated filter
   */
  MessageSubscriptionFilter processInstanceKey(Long processInstanceKey);

  /**
   * Filter by process instance key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the process instance key {@link BasicLongProperty} consumer for the message
   *     subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter processInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by element ID.
   *
   * @param elementId the ID of the element
   * @return the updated filter
   */
  MessageSubscriptionFilter elementId(String elementId);

  /**
   * Filter by element ID using a {@link StringProperty} consumer.
   *
   * @param fn the element ID {@link StringProperty} consumer for the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter elementId(Consumer<StringProperty> fn);

  /**
   * Filter by element instance key.
   *
   * @param elementInstanceKey the key of the element instance
   * @return the updated filter
   */
  MessageSubscriptionFilter elementInstanceKey(Long elementInstanceKey);

  /**
   * Filter by element instance key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the element instance key {@link BasicLongProperty} consumer for the message
   *     subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter elementInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by message subscription state.
   *
   * @param messageSubscriptionState the type of the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter messageSubscriptionState(
      MessageSubscriptionState messageSubscriptionState);

  /**
   * Filter by message subscription state using a {@link MessageSubscriptionStateProperty} consumer.
   *
   * @param fn the message subscription state {@link MessageSubscriptionStateProperty} consumer
   * @return the updated filter
   */
  MessageSubscriptionFilter messageSubscriptionState(Consumer<MessageSubscriptionStateProperty> fn);

  /**
   * Filter by last updated date.
   *
   * @param lastUpdatedDate the last updated date of the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter lastUpdatedDate(OffsetDateTime lastUpdatedDate);

  /**
   * Filter by last updated date using a {@link DateTimeProperty} consumer.
   *
   * @param fn the last updated date {@link DateTimeProperty} consumer for the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter lastUpdatedDate(Consumer<DateTimeProperty> fn);

  /**
   * Filter by message name.
   *
   * @param messageName the name of the message
   * @return the updated filter
   */
  MessageSubscriptionFilter messageName(String messageName);

  /**
   * Filter by message name using a {@link StringProperty} consumer.
   *
   * @param fn the message name {@link StringProperty} consumer for the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter messageName(Consumer<StringProperty> fn);

  /**
   * Filter by correlation key.
   *
   * @param correlationKey the correlation key of the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter correlationKey(String correlationKey);

  /**
   * Filter by correlation key using a {@link StringProperty} consumer.
   *
   * @param fn the correlation key {@link StringProperty} consumer for the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter correlationKey(Consumer<StringProperty> fn);

  /**
   * Filter by tenant ID.
   *
   * @param tenantId the tenant ID of the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter tenantId(String tenantId);

  /**
   * Filter by tenant ID using a {@link StringProperty} consumer.
   *
   * @param fn the tenant ID {@link StringProperty} consumer for the message subscription
   * @return the updated filter
   */
  MessageSubscriptionFilter tenantId(Consumer<StringProperty> fn);
}

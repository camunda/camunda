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

import io.camunda.client.api.search.enums.MessageSubscriptionType;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.MessageSubscriptionTypeProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.time.OffsetDateTime;
import java.util.function.Consumer;

public interface MessageSubscriptionFilter extends SearchRequestFilter {

  /**
   * Filter by message subscription key.
   *
   * @param messageSubscriptionKey the message subscription key to filter by
   * @return a new filter with the message subscription key condition
   */
  MessageSubscriptionFilter messageSubscriptionKey(Long messageSubscriptionKey);

  /**
   * Filter by message subscription key using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a BasicLongProperty to set additional properties
   * @return a new filter with the message subscription key condition
   */
  MessageSubscriptionFilter messageSubscriptionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by process definition ID.
   *
   * @param processDefinitionId the process definition ID to filter by
   * @return a new filter with the process definition ID condition
   */
  MessageSubscriptionFilter processDefinitionId(String processDefinitionId);

  /**
   * Filter by process definition ID using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a StringProperty to set additional properties
   * @return a new filter with the process definition ID condition
   */
  MessageSubscriptionFilter processDefinitionId(Consumer<StringProperty> fn);

  /**
   * Filter by process definition key.
   *
   * @param processDefinitionKey the process definition key to filter by
   * @return a new filter with the process definition key condition
   */
  MessageSubscriptionFilter processDefinitionKey(Long processDefinitionKey);

  /**
   * Filter by process definition key using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a BasicLongProperty to set additional properties
   * @return a new filter with the process definition key condition
   */
  MessageSubscriptionFilter processDefinitionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by process instance key.
   *
   * @param processInstanceKey the process instance key to filter by
   * @return a new filter with the process instance key condition
   */
  MessageSubscriptionFilter processInstanceKey(Long processInstanceKey);

  /**
   * Filter by process instance key using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a BasicLongProperty to set additional properties
   * @return a new filter with the process instance key condition
   */
  MessageSubscriptionFilter processInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by element ID.
   *
   * @param elementId the element ID to filter by
   * @return a new filter with the element ID condition
   */
  MessageSubscriptionFilter elementId(String elementId);

  /**
   * Filter by element ID using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a StringProperty to set additional properties
   * @return a new filter with the element ID condition
   */
  MessageSubscriptionFilter elementId(Consumer<StringProperty> fn);

  /**
   * Filter by element instance key.
   *
   * @param elementInstanceKey the element instance key to filter by
   * @return a new filter with the element instance key condition
   */
  MessageSubscriptionFilter elementInstanceKey(Long elementInstanceKey);

  /**
   * Filter by element instance key using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a BasicLongProperty to set additional properties
   * @return a new filter with the element instance key condition
   */
  MessageSubscriptionFilter elementInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter by message subscription type.
   *
   * @param messageSubscriptionType the message subscription type to filter by
   * @return a new filter with the message subscription type condition
   */
  MessageSubscriptionFilter messageSubscriptionType(
      MessageSubscriptionType messageSubscriptionType);

  /**
   * Filter by message subscription type using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a StringProperty to set additional properties
   * @return a new filter with the message subscription type condition
   */
  MessageSubscriptionFilter messageSubscriptionType(Consumer<MessageSubscriptionTypeProperty> fn);

  // TODO: SWITCH TO ENUM

  /**
   * Filter by last updated date.
   *
   * @param lastUpdatedDate the last updated date to filter by
   * @return a new filter with the last updated date condition
   */
  MessageSubscriptionFilter lastUpdatedDate(OffsetDateTime lastUpdatedDate);

  /**
   * Filter by last updated date using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a BasicStringProperty to set additional properties
   * @return a new filter with the last updated date condition
   */
  MessageSubscriptionFilter lastUpdatedDate(Consumer<DateTimeProperty> fn);

  /**
   * Filter by message name.
   *
   * @param messageName the message name to filter by
   * @return a new filter with the message name condition
   */
  MessageSubscriptionFilter messageName(String messageName);

  /**
   * Filter by message name using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a StringProperty to set additional properties
   * @return a new filter with the message name condition
   */
  MessageSubscriptionFilter messageName(Consumer<StringProperty> fn);

  /**
   * Filter by correlation key.
   *
   * @param correlationKey the correlation key to filter by
   * @return a new filter with the correlation key condition
   */
  MessageSubscriptionFilter correlationKey(String correlationKey);

  /**
   * Filter by correlation key using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a StringProperty to set additional properties
   * @return a new filter with the correlation key condition
   */
  MessageSubscriptionFilter correlationKey(Consumer<StringProperty> fn);

  /**
   * Filter by tenant ID.
   *
   * @param tenantId the tenant ID to filter by
   * @return a new filter with the tenant ID condition
   */
  MessageSubscriptionFilter tenantId(String tenantId);

  /**
   * Filter by tenant ID using a consumer for additional properties.
   *
   * @param fn a consumer that accepts a StringProperty to set additional properties
   * @return a new filter with the tenant ID condition
   */
  MessageSubscriptionFilter tenantId(Consumer<StringProperty> fn);
}

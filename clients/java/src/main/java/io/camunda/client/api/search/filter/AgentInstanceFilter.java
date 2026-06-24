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

import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.filter.builder.AgentInstanceStatusProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.DateTimeProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface AgentInstanceFilter extends SearchRequestFilter {

  /**
   * Filter agent instances by their unique key.
   *
   * @param value the agent instance key
   * @return the updated filter
   */
  AgentInstanceFilter agentInstanceKey(long value);

  /**
   * Filter agent instances by their unique key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the key filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter agentInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter agent instances by their current status.
   *
   * @param value the status to match
   * @return the updated filter
   */
  AgentInstanceFilter status(AgentInstanceStatus value);

  /**
   * Filter agent instances by their current status using an {@link AgentInstanceStatusProperty}
   * consumer.
   *
   * @param fn the status filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter status(Consumer<AgentInstanceStatusProperty> fn);

  /**
   * Filter agent instances by the BPMN element ID.
   *
   * @param value the BPMN element ID
   * @return the updated filter
   */
  AgentInstanceFilter elementId(String value);

  /**
   * Filter agent instances by the BPMN element ID using a {@link StringProperty} consumer.
   *
   * @param fn the element ID filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter elementId(Consumer<StringProperty> fn);

  /**
   * Filter agent instances by the owning process instance key.
   *
   * @param value the process instance key
   * @return the updated filter
   */
  AgentInstanceFilter processInstanceKey(long value);

  /**
   * Filter agent instances by the owning process instance key using a {@link BasicLongProperty}
   * consumer.
   *
   * @param fn the process instance key filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter processInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter agent instances by the root process instance key.
   *
   * @param value the root process instance key
   * @return the updated filter
   */
  AgentInstanceFilter rootProcessInstanceKey(long value);

  /**
   * Filter agent instances by the root process instance key using a {@link BasicLongProperty}
   * consumer.
   *
   * @param fn the root process instance key filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter rootProcessInstanceKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter agent instances by the process definition key.
   *
   * @param value the process definition key
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionKey(long value);

  /**
   * Filter agent instances by the process definition key using a {@link BasicLongProperty}
   * consumer.
   *
   * @param fn the process definition key filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter agent instances by the BPMN process ID.
   *
   * @param value the BPMN process ID
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionId(String value);

  /**
   * Filter agent instances by the BPMN process ID using a {@link StringProperty} consumer.
   *
   * @param fn the process definition ID filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionId(Consumer<StringProperty> fn);

  /**
   * Filter agent instances by the process definition version.
   *
   * @param value the version number
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionVersion(int value);

  /**
   * Filter agent instances by the process definition version using an {@link IntegerProperty}
   * consumer.
   *
   * @param fn the version filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionVersion(Consumer<IntegerProperty> fn);

  /**
   * Filter agent instances by the process definition version tag.
   *
   * @param value the version tag string
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionVersionTag(String value);

  /**
   * Filter agent instances by the process definition version tag using a {@link StringProperty}
   * consumer.
   *
   * @param fn the version tag filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter processDefinitionVersionTag(Consumer<StringProperty> fn);

  /**
   * Filter agent instances by their tenant ID.
   *
   * @param value the tenant ID
   * @return the updated filter
   */
  AgentInstanceFilter tenantId(String value);

  /**
   * Filter agent instances by their tenant ID using a {@link StringProperty} consumer.
   *
   * @param fn the tenant ID filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter tenantId(Consumer<StringProperty> fn);

  /**
   * Filter agent instances by their creation date using a {@link DateTimeProperty} consumer.
   *
   * @param fn the creation date filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter creationDate(Consumer<DateTimeProperty> fn);

  /**
   * Filter agent instances by their last-updated date using a {@link DateTimeProperty} consumer.
   *
   * @param fn the last-updated date filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter lastUpdatedDate(Consumer<DateTimeProperty> fn);

  /**
   * Filter agent instances by their completion date using a {@link DateTimeProperty} consumer.
   *
   * @param fn the completion date filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter completionDate(Consumer<DateTimeProperty> fn);

  /**
   * Filter agent instances by an associated element instance key.
   *
   * @param value the element instance key to match
   * @return the updated filter
   */
  AgentInstanceFilter elementInstanceKey(long value);

  /**
   * Filter agent instances by an associated element instance key using a {@link BasicLongProperty}
   * consumer.
   *
   * @param fn the element instance key filter consumer
   * @return the updated filter
   */
  AgentInstanceFilter elementInstanceKey(Consumer<BasicLongProperty> fn);
}

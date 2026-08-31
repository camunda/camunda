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

import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.filter.builder.AgentDefinitionTypeProperty;
import io.camunda.client.api.search.filter.builder.BasicLongProperty;
import io.camunda.client.api.search.filter.builder.IntegerProperty;
import io.camunda.client.api.search.filter.builder.StringProperty;
import io.camunda.client.api.search.request.TypedFilterableRequest.SearchRequestFilter;
import java.util.function.Consumer;

public interface AgentDefinitionFilter extends SearchRequestFilter {

  /**
   * Filter agent definitions by their unique key.
   *
   * @param value the agent definition key
   * @return the updated filter
   */
  AgentDefinitionFilter agentDefinitionKey(long value);

  /**
   * Filter agent definitions by their unique key using a {@link BasicLongProperty} consumer.
   *
   * @param fn the key filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter agentDefinitionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter agent definitions by the kind of agent they describe.
   *
   * @param value the agent type to match
   * @return the updated filter
   */
  AgentDefinitionFilter agentType(AgentDefinitionType value);

  /**
   * Filter agent definitions by the kind of agent they describe using an {@link
   * AgentDefinitionTypeProperty} consumer.
   *
   * @param fn the agent type filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter agentType(Consumer<AgentDefinitionTypeProperty> fn);

  /**
   * Filter agent definitions by the human-readable name of the process element that owns them.
   *
   * @param value the name to match
   * @return the updated filter
   */
  AgentDefinitionFilter name(String value);

  /**
   * Filter agent definitions by the human-readable name of the process element that owns them using
   * a {@link StringProperty} consumer.
   *
   * @param fn the name filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter name(Consumer<StringProperty> fn);

  /**
   * Filter agent definitions by the BPMN element ID of the process element that owns them.
   *
   * @param value the BPMN element ID
   * @return the updated filter
   */
  AgentDefinitionFilter elementId(String value);

  /**
   * Filter agent definitions by the BPMN element ID using a {@link StringProperty} consumer.
   *
   * @param fn the element ID filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter elementId(Consumer<StringProperty> fn);

  /**
   * Filter agent definitions by the BPMN process ID of the process definition that owns them.
   *
   * @param value the BPMN process ID
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionId(String value);

  /**
   * Filter agent definitions by the BPMN process ID using a {@link StringProperty} consumer.
   *
   * @param fn the process definition ID filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionId(Consumer<StringProperty> fn);

  /**
   * Filter agent definitions by the key of the process definition that owns them.
   *
   * @param value the process definition key
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionKey(long value);

  /**
   * Filter agent definitions by the process definition key using a {@link BasicLongProperty}
   * consumer.
   *
   * @param fn the process definition key filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionKey(Consumer<BasicLongProperty> fn);

  /**
   * Filter agent definitions by the version of the process definition that owns them.
   *
   * @param value the version number
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionVersion(int value);

  /**
   * Filter agent definitions by the process definition version using an {@link IntegerProperty}
   * consumer.
   *
   * @param fn the version filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionVersion(Consumer<IntegerProperty> fn);

  /**
   * Filter agent definitions by the version tag of the process definition that owns them.
   *
   * @param value the version tag string
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionVersionTag(String value);

  /**
   * Filter agent definitions by the process definition version tag using a {@link StringProperty}
   * consumer.
   *
   * @param fn the version tag filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter processDefinitionVersionTag(Consumer<StringProperty> fn);

  /**
   * Filter agent definitions by their tenant ID.
   *
   * @param value the tenant ID
   * @return the updated filter
   */
  AgentDefinitionFilter tenantId(String value);

  /**
   * Filter agent definitions by their tenant ID using a {@link StringProperty} consumer.
   *
   * @param fn the tenant ID filter consumer
   * @return the updated filter
   */
  AgentDefinitionFilter tenantId(Consumer<StringProperty> fn);
}

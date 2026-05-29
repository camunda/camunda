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
package io.camunda.client.api.search.response;

import io.camunda.client.api.search.enums.AgentInstanceStatus;
import java.time.OffsetDateTime;
import java.util.List;

/** Represents an agent instance returned from the Camunda REST API. */
public interface AgentInstance {

  /** Returns the unique key identifying this agent instance. */
  long getAgentInstanceKey();

  /** Returns the current execution status of the agent instance. */
  AgentInstanceStatus getStatus();

  /**
   * Returns the static definition of the agent, containing model, provider, and system prompt set
   * at creation time.
   */
  Definition getDefinition();

  /** Returns the aggregated runtime metrics for this agent instance. */
  Metrics getMetrics();

  /** Returns the configured execution limits for this agent instance, set once at creation time. */
  Limits getLimits();

  /** Returns the list of tools available to this agent. */
  List<Tool> getTools();

  /** Returns the BPMN element ID of the ad-hoc sub-process or AI agent task. */
  String getElementId();

  /** Returns the key of the process instance that owns this agent instance. */
  long getProcessInstanceKey();

  /** Returns the key of the root process instance. */
  Long getRootProcessInstanceKey();

  /** Returns the key of the process definition associated with this agent instance. */
  long getProcessDefinitionKey();

  /** Returns the BPMN process ID of the associated process definition. */
  String getProcessDefinitionId();

  /** Returns the version number of the associated process definition. */
  int getProcessDefinitionVersion();

  /**
   * Returns the version tag of the associated process definition, or {@code null} if none was
   * configured.
   */
  String getProcessDefinitionVersionTag();

  /** Returns the tenant ID of this agent instance. */
  String getTenantId();

  /** Returns the timestamp when this agent instance was created. */
  OffsetDateTime getCreationDate();

  /** Returns the timestamp of the most recent update to this agent instance. */
  OffsetDateTime getLastUpdatedDate();

  /** Returns the timestamp when this agent instance completed, or {@code null} if still running. */
  OffsetDateTime getCompletionDate();

  /** Returns the keys of all element instances associated with this agent instance. */
  List<Long> getElementInstanceKeys();

  /** Static definition of the agent, set once at creation time. */
  interface Definition {

    /** Returns the LLM model identifier (e.g. {@code gpt-4o}). */
    String getModel();

    /** Returns the LLM provider (e.g. {@code openai} or {@code anthropic}). */
    String getProvider();

    /** Returns the system prompt configured for this agent instance. */
    String getSystemPrompt();
  }

  /** Aggregated runtime metrics for this agent instance. */
  interface Metrics {

    /** Returns the total number of input tokens consumed across all model calls. */
    long getInputTokens();

    /** Returns the total number of output tokens produced across all model calls. */
    long getOutputTokens();

    /** Returns the total number of LLM calls made. */
    int getModelCalls();

    /** Returns the total number of tool calls made. */
    int getToolCalls();
  }

  /** Configured execution limits for this agent instance. */
  interface Limits {

    /** Returns the maximum number of LLM calls allowed, or {@code -1} for no limit. */
    int getMaxModelCalls();

    /** Returns the maximum number of tool calls allowed, or {@code -1} for no limit. */
    int getMaxToolCalls();

    /** Returns the maximum total tokens allowed, or {@code -1} for no limit. */
    long getMaxTokens();
  }

  /** A tool available to the agent. */
  interface Tool {

    /** Returns the tool name as visible to the LLM. */
    String getName();

    /** Returns a human-readable description of the tool, or {@code null} if none was configured. */
    String getDescription();

    /**
     * Returns the BPMN element ID of the tool element within the ad-hoc sub-process, or {@code
     * null} if not applicable.
     */
    String getElementId();
  }
}

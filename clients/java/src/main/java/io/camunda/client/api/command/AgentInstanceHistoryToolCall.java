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
package io.camunda.client.api.command;

import java.util.Map;

/** A tool call associated with an ASSISTANT agent instance history item. */
public final class AgentInstanceHistoryToolCall {
  private String toolCallId;
  private String toolName;
  private String elementId;
  private Map<String, Object> arguments;

  public AgentInstanceHistoryToolCall toolCallId(final String toolCallId) {
    this.toolCallId = toolCallId;
    return this;
  }

  public AgentInstanceHistoryToolCall toolName(final String toolName) {
    this.toolName = toolName;
    return this;
  }

  public AgentInstanceHistoryToolCall elementId(final String elementId) {
    this.elementId = elementId;
    return this;
  }

  public AgentInstanceHistoryToolCall arguments(final Map<String, Object> arguments) {
    this.arguments = arguments;
    return this;
  }

  public String getToolCallId() {
    return toolCallId;
  }

  public String getToolName() {
    return toolName;
  }

  public String getElementId() {
    return elementId;
  }

  public Map<String, Object> getArguments() {
    return arguments;
  }
}

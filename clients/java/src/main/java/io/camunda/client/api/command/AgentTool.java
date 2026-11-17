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

/** Represents a tool available to the agent instance. */
public interface AgentTool {
  String getName();

  String getDescription();

  String getElementId();

  /**
   * Creates a tool with the given name and no description or element ID.
   *
   * @param name the tool name. Must not be blank.
   * @return a new {@link AgentTool}
   */
  static AgentTool of(final String name) {
    return of(name, null, null);
  }

  /**
   * Creates a tool with the given name, description, and element ID.
   *
   * @param name the tool name. Must not be blank.
   * @param description optional description of the tool
   * @param elementId optional ID of the BPMN element providing this tool
   * @return a new {@link AgentTool}
   */
  static AgentTool of(final String name, final String description, final String elementId) {
    return new AgentTool() {
      @Override
      public String getName() {
        return name;
      }

      @Override
      public String getDescription() {
        return description;
      }

      @Override
      public String getElementId() {
        return elementId;
      }
    };
  }
}

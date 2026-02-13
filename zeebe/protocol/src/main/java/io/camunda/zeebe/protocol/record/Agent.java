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
package io.camunda.zeebe.protocol.record;

import org.immutables.value.Value;

/**
 * Represents an agent that can trigger events and commands within the system.
 *
 * <p>This interface is used to trace events and commands that were issued directly or indirectly
 * through agentic interactions. It enables capturing the complete trail of changes that occurred as
 * a result of an agent's actions, providing full traceability of which agent (process) triggered
 * all subsequent changes in the system.
 *
 * <p>By associating an {@code Agent} with records, the system can:
 *
 * <ul>
 *   <li>Track the origin of events and commands back to the initiating agent
 *   <li>Build a complete audit trail of all changes triggered by an agent
 *   <li>Understand the causal chain of events resulting from agentic interactions
 *   <li>Enable debugging and analysis of agent-driven workflows
 * </ul>
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgent.Builder.class)
public interface Agent {
  /**
   * Returns the elementId that the agent is associated with
   *
   * <p>for example, the id of the adhoc subprocess that represents the agent in the process model.
   *
   * @return the elementId
   */
  String getElementId();
}

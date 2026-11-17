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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

/**
 * Marks a {@code serviceTask} or {@code adHocSubProcess} element as an agent definition: the shared
 * design-time signal that both the agent registry and external-agent features rely on to detect
 * agents at deploy time.
 */
public interface ZeebeAgentDefinition extends BpmnModelElementInstance {

  /**
   * @return the agent type declared on the marker, identifying both the agent's origin
   *     (Camunda-native vs. external) and, for Camunda-native agents, the hosting element shape
   */
  ZeebeAgentType getAgentType();

  void setAgentType(ZeebeAgentType agentType);
}

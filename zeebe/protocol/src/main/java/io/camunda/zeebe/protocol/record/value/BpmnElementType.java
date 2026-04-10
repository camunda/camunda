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
package io.camunda.zeebe.protocol.record.value;

import java.util.Arrays;
import java.util.Optional;

public enum BpmnElementType {

  // Default
  UNSPECIFIED(null, false),

  // Containers
  PROCESS("process", true),
  SUB_PROCESS("subProcess", true),
  EVENT_SUB_PROCESS(null, true),
  AD_HOC_SUB_PROCESS("adHocSubProcess", true),
  AD_HOC_SUB_PROCESS_INNER_INSTANCE(null, true),

  // Events
  START_EVENT("startEvent", false),
  INTERMEDIATE_CATCH_EVENT("intermediateCatchEvent", false),
  INTERMEDIATE_THROW_EVENT("intermediateThrowEvent", false),
  BOUNDARY_EVENT("boundaryEvent", false),
  END_EVENT("endEvent", false),

  // Tasks
  SERVICE_TASK("serviceTask", false),
  RECEIVE_TASK("receiveTask", false),
  USER_TASK("userTask", false),
  MANUAL_TASK("manualTask", false),
  TASK("task", false),

  // Gateways
  EXCLUSIVE_GATEWAY("exclusiveGateway", false),
  PARALLEL_GATEWAY("parallelGateway", false),
  EVENT_BASED_GATEWAY("eventBasedGateway", false),
  INCLUSIVE_GATEWAY("inclusiveGateway", false),

  // Other
  SEQUENCE_FLOW("sequenceFlow", false),
  MULTI_INSTANCE_BODY(null, true),
  // Note that a call activity is not considered to be a container element. Whilst it creates a
  // child process instance, it does not contain direct child elements itself.
  CALL_ACTIVITY("callActivity", false),

  BUSINESS_RULE_TASK("businessRuleTask", false),
  SCRIPT_TASK("scriptTask", false),
  SEND_TASK("sendTask", false);

  private final String elementTypeName;

  /**
   * A container element is defined as an element that contains child elements within, such as a
   * sub-process.
   */
  private final boolean isContainerElement;

  BpmnElementType(final String elementTypeName, final boolean isContainerElement) {
    this.elementTypeName = elementTypeName;
    this.isContainerElement = isContainerElement;
  }

  public Optional<String> getElementTypeName() {
    return Optional.ofNullable(elementTypeName);
  }

  public static BpmnElementType bpmnElementTypeFor(final String elementTypeName) {
    return Arrays.stream(values())
        .filter(
            bpmnElementType ->
                bpmnElementType.elementTypeName != null
                    && bpmnElementType.elementTypeName.equals(elementTypeName))
        .findFirst()
        .orElseThrow(
            () -> new RuntimeException("Unsupported BPMN element of type " + elementTypeName));
  }

  public boolean isContainerElement() {
    return isContainerElement;
  }
}

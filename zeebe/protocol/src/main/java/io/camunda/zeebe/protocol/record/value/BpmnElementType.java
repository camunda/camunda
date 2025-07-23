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
  UNSPECIFIED(null),

  // Containers
  PROCESS("process"),
  SUB_PROCESS("subProcess"),
  EVENT_SUB_PROCESS(null),
  AD_HOC_SUB_PROCESS("adHocSubProcess"),
  AD_HOC_SUB_PROCESS_INNER_INSTANCE(null),

  // Events
  START_EVENT("startEvent"),
  INTERMEDIATE_CATCH_EVENT("intermediateCatchEvent"),
  INTERMEDIATE_THROW_EVENT("intermediateThrowEvent"),
  BOUNDARY_EVENT("boundaryEvent"),
  END_EVENT("endEvent"),

  // Tasks
  SERVICE_TASK("serviceTask"),
  RECEIVE_TASK("receiveTask"),
  USER_TASK("userTask"),
  MANUAL_TASK("manualTask"),
  TASK("task"),

  // Gateways
  EXCLUSIVE_GATEWAY("exclusiveGateway"),
  PARALLEL_GATEWAY("parallelGateway"),
  EVENT_BASED_GATEWAY("eventBasedGateway"),
  INCLUSIVE_GATEWAY("inclusiveGateway"),

  // Other
  SEQUENCE_FLOW("sequenceFlow"),
  MULTI_INSTANCE_BODY(null),
  CALL_ACTIVITY("callActivity"),

  BUSINESS_RULE_TASK("businessRuleTask"),
  SCRIPT_TASK("scriptTask"),
  SEND_TASK("sendTask");

  private final String elementTypeName;

  BpmnElementType(final String elementTypeName) {
    this.elementTypeName = elementTypeName;
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
}

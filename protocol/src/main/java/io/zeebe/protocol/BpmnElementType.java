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
package io.zeebe.protocol;

public enum BpmnElementType {

  // Default
  UNSPECIFIED,

  // Containers
  PROCESS,
  SUB_PROCESS,

  // Events
  START_EVENT,
  INTERMEDIATE_CATCH_EVENT,
  BOUNDARY_EVENT,
  END_EVENT,

  // Tasks
  SERVICE_TASK,
  RECEIVE_TASK,

  // Gateways
  EXCLUSIVE_GATEWAY,
  PARALLEL_GATEWAY,
  EVENT_BASED_GATEWAY,

  // Other
  SEQUENCE_FLOW;

  public static BpmnElementType bpmnElementTypeFor(String elementTypeName) {
    switch (elementTypeName) {
      case "process":
        return BpmnElementType.PROCESS;
      case "subProcess":
        return BpmnElementType.SUB_PROCESS;
      case "startEvent":
        return BpmnElementType.START_EVENT;
      case "intermediateCatchEvent":
        return BpmnElementType.INTERMEDIATE_CATCH_EVENT;
      case "boundaryEvent":
        return BpmnElementType.BOUNDARY_EVENT;
      case "endEvent":
        return BpmnElementType.END_EVENT;
      case "serviceTask":
        return BpmnElementType.SERVICE_TASK;
      case "receiveTask":
        return BpmnElementType.RECEIVE_TASK;
      case "exclusiveGateway":
        return BpmnElementType.EXCLUSIVE_GATEWAY;
      case "eventBasedGateway":
        return BpmnElementType.EVENT_BASED_GATEWAY;
      case "parallelGateway":
        return BpmnElementType.PARALLEL_GATEWAY;
      case "sequenceFlow":
        return BpmnElementType.SEQUENCE_FLOW;
      default:
        throw new RuntimeException("Unsupported BPMN element of type " + elementTypeName);
    }
  }
}

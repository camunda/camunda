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
package io.zeebe.model.bpmn;

public enum BpmnAspect {
  NONE,

  // exactly one outgoing sequence flow
  TAKE_SEQUENCE_FLOW,

  // end event, no outgoing sequence flow
  CONSUME_TOKEN,

  // xor-gateway
  EXCLUSIVE_SPLIT,

  // or-gateway
  INCLUSIVE_SPLIT,

  // and-gateway
  PARALLEL_SPLIT,

  // joining and-/or-gateway
  MERGE,
}

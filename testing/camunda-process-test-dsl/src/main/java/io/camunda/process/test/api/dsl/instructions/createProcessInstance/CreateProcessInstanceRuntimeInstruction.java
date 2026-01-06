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
package io.camunda.process.test.api.dsl.instructions.createProcessInstance;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

/** An instruction to affect the runtime behavior of the process instance. */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = CreateProcessInstanceTerminateRuntimeInstruction.class,
      name = CreateProcessInstanceRuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
})
public interface CreateProcessInstanceRuntimeInstruction {

  /**
   * The type of the runtime instruction. It should be one of {@link
   * CreateProcessInstanceRuntimeInstructionType}.
   *
   * @return the type
   * @see CreateProcessInstanceRuntimeInstructionType
   */
  String getType();
}

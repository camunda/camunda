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
package io.camunda.process.test.api.dsl.instructions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.process.test.api.dsl.ProcessDefinitionSelector;
import io.camunda.process.test.api.dsl.TestCaseInstruction;
import io.camunda.process.test.api.dsl.TestCaseInstructionType;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.CreateProcessInstanceRuntimeInstruction;
import io.camunda.process.test.api.dsl.instructions.createProcessInstance.CreateProcessInstanceStartInstruction;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

/** An instruction to create a new process instance. */
@Value.Immutable
@JsonDeserialize(builder = ImmutableCreateProcessInstanceInstruction.Builder.class)
public interface CreateProcessInstanceInstruction extends TestCaseInstruction {

  @Value.Default
  @Override
  default String getType() {
    return TestCaseInstructionType.CREATE_PROCESS_INSTANCE;
  }

  /**
   * The selector to identify the process definition to create the instance for.
   *
   * @return the process definition selector
   */
  ProcessDefinitionSelector getProcessDefinitionSelector();

  /**
   * The variables to create the process instance with. Optional.
   *
   * @return the variables or an empty map if no variables are set
   */
  Map<String, Object> getVariables();

  /**
   * The instructions to execute when starting the process instance. Optional.
   *
   * @return the start instructions or an empty list if no instructions are set
   */
  List<CreateProcessInstanceStartInstruction> getStartInstructions();

  /**
   * The instructions to affect the runtime behavior of the process instance. Optional.
   *
   * @return the runtime instructions or an empty list if no instructions are set
   */
  List<CreateProcessInstanceRuntimeInstruction> getRuntimeInstructions();
}

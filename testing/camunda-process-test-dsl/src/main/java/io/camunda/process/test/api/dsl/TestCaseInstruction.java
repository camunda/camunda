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
package io.camunda.process.test.api.dsl;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import io.camunda.process.test.api.dsl.instructions.AssertElementInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.AssertElementInstancesInstruction;
import io.camunda.process.test.api.dsl.instructions.AssertProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.AssertProcessInstanceMessageSubscriptionInstruction;
import io.camunda.process.test.api.dsl.instructions.AssertUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.AssertVariablesInstruction;
import io.camunda.process.test.api.dsl.instructions.CompleteUserTaskInstruction;
import io.camunda.process.test.api.dsl.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.dsl.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.dsl.instructions.PublishMessageInstruction;

/** An instruction to define an action or an assertion to be performed in a test case. */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AssertElementInstanceInstruction.class,
      name = TestCaseInstructionType.ASSERT_ELEMENT_INSTANCE),
  @JsonSubTypes.Type(
      value = AssertElementInstancesInstruction.class,
      name = TestCaseInstructionType.ASSERT_ELEMENT_INSTANCES),
  @JsonSubTypes.Type(
      value = AssertProcessInstanceInstruction.class,
      name = TestCaseInstructionType.ASSERT_PROCESS_INSTANCE),
  @JsonSubTypes.Type(
      value = AssertProcessInstanceMessageSubscriptionInstruction.class,
      name = TestCaseInstructionType.ASSERT_PROCESS_INSTANCE_MESSAGE_SUBSCRIPTION),
  @JsonSubTypes.Type(
      value = AssertUserTaskInstruction.class,
      name = TestCaseInstructionType.ASSERT_USER_TASK),
  @JsonSubTypes.Type(
      value = AssertVariablesInstruction.class,
      name = TestCaseInstructionType.ASSERT_VARIABLES),
  @JsonSubTypes.Type(
      value = CompleteUserTaskInstruction.class,
      name = TestCaseInstructionType.COMPLETE_USER_TASK),
  @JsonSubTypes.Type(
      value = CreateProcessInstanceInstruction.class,
      name = TestCaseInstructionType.CREATE_PROCESS_INSTANCE),
  @JsonSubTypes.Type(
      value = MockJobWorkerCompleteJobInstruction.class,
      name = TestCaseInstructionType.MOCK_JOB_WORKER_COMPLETE_JOB),
  @JsonSubTypes.Type(
      value = PublishMessageInstruction.class,
      name = TestCaseInstructionType.PUBLISH_MESSAGE)
})
public interface TestCaseInstruction {

  /**
   * The type of the instruction. It should be one of {@link TestCaseInstructionType}.
   *
   * @return the type
   * @see TestCaseInstructionType
   */
  String getType();
}

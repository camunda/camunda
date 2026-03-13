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
package io.camunda.process.test.api.testCases;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import io.camunda.process.test.api.testCases.instructions.AssertDecisionInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertElementInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertElementInstancesInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertProcessInstanceMessageSubscriptionInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertVariableJudgeInstruction;
import io.camunda.process.test.api.testCases.instructions.AssertVariablesInstruction;
import io.camunda.process.test.api.testCases.instructions.BroadcastSignalInstruction;
import io.camunda.process.test.api.testCases.instructions.CompleteJobAdHocSubProcessInstruction;
import io.camunda.process.test.api.testCases.instructions.CompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.CompleteJobUserTaskListenerInstruction;
import io.camunda.process.test.api.testCases.instructions.CompleteUserTaskInstruction;
import io.camunda.process.test.api.testCases.instructions.CorrelateMessageInstruction;
import io.camunda.process.test.api.testCases.instructions.CreateProcessInstanceInstruction;
import io.camunda.process.test.api.testCases.instructions.EvaluateConditionalStartEventInstruction;
import io.camunda.process.test.api.testCases.instructions.EvaluateDecisionInstruction;
import io.camunda.process.test.api.testCases.instructions.IncreaseTimeInstruction;
import io.camunda.process.test.api.testCases.instructions.MockChildProcessInstruction;
import io.camunda.process.test.api.testCases.instructions.MockDmnDecisionInstruction;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.MockJobWorkerThrowBpmnErrorInstruction;
import io.camunda.process.test.api.testCases.instructions.PublishMessageInstruction;
import io.camunda.process.test.api.testCases.instructions.ResolveIncidentInstruction;
import io.camunda.process.test.api.testCases.instructions.SetTimeInstruction;
import io.camunda.process.test.api.testCases.instructions.ThrowBpmnErrorFromJobInstruction;
import io.camunda.process.test.api.testCases.instructions.UpdateVariablesInstruction;

/** An instruction to define an action or an assertion to be performed in a test case. */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = As.EXISTING_PROPERTY,
    property = "type",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = AssertDecisionInstruction.class,
      name = TestCaseInstructionType.ASSERT_DECISION),
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
      value = AssertVariableJudgeInstruction.class,
      name = TestCaseInstructionType.ASSERT_VARIABLE_JUDGE),
  @JsonSubTypes.Type(
      value = AssertVariablesInstruction.class,
      name = TestCaseInstructionType.ASSERT_VARIABLES),
  @JsonSubTypes.Type(
      value = BroadcastSignalInstruction.class,
      name = TestCaseInstructionType.BROADCAST_SIGNAL),
  @JsonSubTypes.Type(
      value = CompleteJobInstruction.class,
      name = TestCaseInstructionType.COMPLETE_JOB),
  @JsonSubTypes.Type(
      value = CompleteJobAdHocSubProcessInstruction.class,
      name = TestCaseInstructionType.COMPLETE_JOB_AD_HOC_SUB_PROCESS),
  @JsonSubTypes.Type(
      value = CompleteJobUserTaskListenerInstruction.class,
      name = TestCaseInstructionType.COMPLETE_JOB_USER_TASK_LISTENER),
  @JsonSubTypes.Type(
      value = CompleteUserTaskInstruction.class,
      name = TestCaseInstructionType.COMPLETE_USER_TASK),
  @JsonSubTypes.Type(
      value = CreateProcessInstanceInstruction.class,
      name = TestCaseInstructionType.CREATE_PROCESS_INSTANCE),
  @JsonSubTypes.Type(
      value = EvaluateConditionalStartEventInstruction.class,
      name = TestCaseInstructionType.EVALUATE_CONDITIONAL_START_EVENT),
  @JsonSubTypes.Type(
      value = EvaluateDecisionInstruction.class,
      name = TestCaseInstructionType.EVALUATE_DECISION),
  @JsonSubTypes.Type(
      value = IncreaseTimeInstruction.class,
      name = TestCaseInstructionType.INCREASE_TIME),
  @JsonSubTypes.Type(
      value = MockChildProcessInstruction.class,
      name = TestCaseInstructionType.MOCK_CHILD_PROCESS),
  @JsonSubTypes.Type(
      value = MockDmnDecisionInstruction.class,
      name = TestCaseInstructionType.MOCK_DMN_DECISION),
  @JsonSubTypes.Type(
      value = MockJobWorkerCompleteJobInstruction.class,
      name = TestCaseInstructionType.MOCK_JOB_WORKER_COMPLETE_JOB),
  @JsonSubTypes.Type(
      value = MockJobWorkerThrowBpmnErrorInstruction.class,
      name = TestCaseInstructionType.MOCK_JOB_WORKER_THROW_BPMN_ERROR),
  @JsonSubTypes.Type(
      value = PublishMessageInstruction.class,
      name = TestCaseInstructionType.PUBLISH_MESSAGE),
  @JsonSubTypes.Type(
      value = CorrelateMessageInstruction.class,
      name = TestCaseInstructionType.CORRELATE_MESSAGE),
  @JsonSubTypes.Type(
      value = ResolveIncidentInstruction.class,
      name = TestCaseInstructionType.RESOLVE_INCIDENT),
  @JsonSubTypes.Type(value = SetTimeInstruction.class, name = TestCaseInstructionType.SET_TIME),
  @JsonSubTypes.Type(
      value = ThrowBpmnErrorFromJobInstruction.class,
      name = TestCaseInstructionType.THROW_BPMN_ERROR_FROM_JOB),
  @JsonSubTypes.Type(
      value = UpdateVariablesInstruction.class,
      name = TestCaseInstructionType.UPDATE_VARIABLES)
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

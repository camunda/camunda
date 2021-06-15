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
package io.camunda.zeebe.model.bpmn.validation;

import static io.camunda.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.model.bpmn.builder.AbstractJobWorkerTaskBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ZeebeJobWorkerTaskValidationTest {

  @ParameterizedTest
  @MethodSource("jobWorkerTaskBuilderProvider")
  @DisplayName("task with static job type and retries")
  void validStaticJobTypeAndRetries(final JobWorkerTaskBuilder taskBuilder) {

    final BpmnModelInstance process =
        processWithTask(taskBuilder, task -> task.zeebeJobType("service").zeebeJobRetries("5"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerTaskBuilderProvider")
  @DisplayName("task with job type and retries expression")
  void validJobTypeAndRetriesExpression(final JobWorkerTaskBuilder taskBuilder) {

    final BpmnModelInstance process =
        processWithTask(
            taskBuilder,
            task ->
                task.zeebeJobTypeExpression("serviceType").zeebeJobRetriesExpression("jobRetries"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerTaskBuilderProvider")
  @DisplayName("task with custom header")
  void validCustomHeader(final JobWorkerTaskBuilder taskBuilder) {

    final BpmnModelInstance process =
        processWithTask(
            taskBuilder, task -> task.zeebeJobType("service").zeebeTaskHeader("priority", "high"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerTaskBuilderProvider")
  @DisplayName("task without job type")
  void missingJobType(final JobWorkerTaskBuilder taskBuilder) {

    final BpmnModelInstance process = processWithTask(taskBuilder, task -> task.id("task"));

    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect("task", "Must have exactly one 'zeebe:taskDefinition' extension element"));
  }

  @ParameterizedTest
  @MethodSource("jobWorkerTaskBuilderProvider")
  @DisplayName("task with empty job type")
  void emptyJobType(final JobWorkerTaskBuilder taskBuilder) {

    final BpmnModelInstance process = processWithTask(taskBuilder, task -> task.zeebeJobType(""));

    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeTaskDefinition.class, "Task type must be present and not empty"));
  }

  private BpmnModelInstance processWithTask(
      final JobWorkerTaskBuilder taskBuilder,
      final Consumer<AbstractJobWorkerTaskBuilder<?, ?>> taskModifier) {

    final StartEventBuilder processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    final AbstractJobWorkerTaskBuilder<?, ?> jobWorkerTaskBuilder =
        taskBuilder.build(processBuilder);
    taskModifier.accept(jobWorkerTaskBuilder);
    return jobWorkerTaskBuilder.endEvent().done();
  }

  private static Stream<JobWorkerTaskBuilder> jobWorkerTaskBuilderProvider() {
    return Stream.of(
        JobWorkerTaskBuilder.of("serviceTask", AbstractFlowNodeBuilder::serviceTask),
        JobWorkerTaskBuilder.of("businessRuleTask", AbstractFlowNodeBuilder::businessRuleTask),
        JobWorkerTaskBuilder.of("scriptTask", AbstractFlowNodeBuilder::scriptTask),
        JobWorkerTaskBuilder.of("sendTask", AbstractFlowNodeBuilder::sendTask));
  }

  private static final class JobWorkerTaskBuilder {

    private final String taskType;
    private final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>>
        builder;

    private JobWorkerTaskBuilder(
        final String taskType,
        final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>> builder) {
      this.taskType = taskType;
      this.builder = builder;
    }

    public AbstractJobWorkerTaskBuilder<?, ?> build(
        final AbstractFlowNodeBuilder<?, ?> processBuilder) {
      return builder.apply(processBuilder);
    }

    private static JobWorkerTaskBuilder of(
        final String taskType,
        final Function<AbstractFlowNodeBuilder<?, ?>, AbstractJobWorkerTaskBuilder<?, ?>> builder) {
      return new JobWorkerTaskBuilder(taskType, builder);
    }

    @Override
    public String toString() {
      return taskType;
    }
  }
}

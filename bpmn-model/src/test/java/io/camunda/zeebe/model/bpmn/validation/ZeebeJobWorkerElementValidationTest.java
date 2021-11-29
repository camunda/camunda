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
import io.camunda.zeebe.model.bpmn.builder.AbstractThrowEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.model.bpmn.builder.ZeebeJobWorkerElementBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ZeebeJobWorkerElementValidationTest {

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with static job type and retries")
  void validStaticJobTypeAndRetries(final JobWorkerElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(
            elementBuilder, element -> element.zeebeJobType("service").zeebeJobRetries("5"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with job type and retries expression")
  void validJobTypeAndRetriesExpression(final JobWorkerElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(
            elementBuilder,
            element ->
                element
                    .zeebeJobTypeExpression("serviceType")
                    .zeebeJobRetriesExpression("jobRetries"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with custom header")
  void validCustomHeader(final JobWorkerElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(
            elementBuilder,
            element -> element.zeebeJobType("service").zeebeTaskHeader("priority", "high"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element without job type")
  void missingJobType(final JobWorkerElementBuilder elementBuilder) {

    final BpmnModelInstance process = processWithJobWorkerElement(elementBuilder, element -> {});

    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect("task", "Must have exactly one 'zeebe:taskDefinition' extension element"));
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with empty job type")
  void emptyJobType(final JobWorkerElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(elementBuilder, element -> element.zeebeJobType(""));

    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskDefinition.class, "Attribute 'type' must be present and not empty"));
  }

  private BpmnModelInstance processWithJobWorkerElement(
      final JobWorkerElementBuilder elementBuilder,
      final Consumer<ZeebeJobWorkerElementBuilder<?>> elementModifier) {

    final StartEventBuilder processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    final AbstractFlowNodeBuilder<?, ?> jobWorkerElementBuilder =
        elementBuilder.build(processBuilder, elementModifier);
    return jobWorkerElementBuilder.id("task").done();
  }

  private static Stream<JobWorkerElementBuilder> jobWorkerElementBuilderProvider() {
    return Stream.of(
        JobWorkerElementBuilder.of("serviceTask", AbstractFlowNodeBuilder::serviceTask),
        JobWorkerElementBuilder.of("scriptTask", AbstractFlowNodeBuilder::scriptTask),
        JobWorkerElementBuilder.of("sendTask", AbstractFlowNodeBuilder::sendTask),
        JobWorkerElementBuilder.of(
            "message end event",
            process ->
                process.endEvent("message", AbstractThrowEventBuilder::messageEventDefinition)),
        JobWorkerElementBuilder.of(
            "intermediate message throw event",
            process ->
                process.intermediateThrowEvent(
                    "message", AbstractThrowEventBuilder::messageEventDefinition)));
  }

  private static final class JobWorkerElementBuilder {

    private final String elementType;
    private final Function<AbstractFlowNodeBuilder<?, ?>, AbstractFlowNodeBuilder<?, ?>> builder;

    private <T extends AbstractFlowNodeBuilder<?, ?> & ZeebeJobWorkerElementBuilder<T>>
        JobWorkerElementBuilder(
            final String elementType, final Function<AbstractFlowNodeBuilder<?, ?>, T> builder) {
      this.elementType = elementType;
      this.builder = builder::apply;
    }

    public AbstractFlowNodeBuilder<?, ?> build(
        final AbstractFlowNodeBuilder<?, ?> processBuilder,
        final Consumer<ZeebeJobWorkerElementBuilder<?>> builderConsumer) {
      final AbstractFlowNodeBuilder<?, ?> elementBuilder = builder.apply(processBuilder);
      builderConsumer.accept((ZeebeJobWorkerElementBuilder<?>) elementBuilder);
      return elementBuilder;
    }

    private static <T extends AbstractFlowNodeBuilder<?, ?> & ZeebeJobWorkerElementBuilder<T>>
        JobWorkerElementBuilder of(
            final String elementType, final Function<AbstractFlowNodeBuilder<?, ?>, T> builder) {
      return new JobWorkerElementBuilder(elementType, builder);
    }

    @Override
    public String toString() {
      return elementType;
    }
  }
}

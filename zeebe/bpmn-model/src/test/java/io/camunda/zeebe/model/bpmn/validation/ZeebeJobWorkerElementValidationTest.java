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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ZeebeJobWorkerElementValidationTest {

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with static job type and retries")
  void validStaticJobTypeAndRetries(final BpmnElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(
            elementBuilder, element -> element.zeebeJobType("service").zeebeJobRetries("5"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with job type and retries expression")
  void validJobTypeAndRetriesExpression(final BpmnElementBuilder elementBuilder) {

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
  void validCustomHeader(final BpmnElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(
            elementBuilder,
            element -> element.zeebeJobType("service").zeebeTaskHeader("priority", "high"));

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element without job type or publish message")
  void missingJobTypeOrPublishMessage(final BpmnElementBuilder elementBuilder) {
    String message =
        "Must have either one 'zeebe:publishMessage' or one 'zeebe:taskDefinition' extension element";
    if ("serviceTask".equals(elementBuilder.getElementType())) {
      message = "Must have exactly one 'zeebe:taskDefinition' extension element";
    }
    final BpmnModelInstance process = processWithJobWorkerElement(elementBuilder, element -> {});

    ProcessValidationUtil.assertThatProcessHasViolations(process, expect("task", message));
  }

  @ParameterizedTest
  @MethodSource("jobWorkerElementBuilderProvider")
  @DisplayName("element with empty job type")
  void emptyJobType(final BpmnElementBuilder elementBuilder) {

    final BpmnModelInstance process =
        processWithJobWorkerElement(elementBuilder, element -> element.zeebeJobType(""));

    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskDefinition.class, "Attribute 'type' must be present and not empty"));
  }

  private BpmnModelInstance processWithJobWorkerElement(
      final BpmnElementBuilder elementBuilder,
      final Consumer<ZeebeJobWorkerElementBuilder<?>> elementModifier) {

    final StartEventBuilder processBuilder = Bpmn.createExecutableProcess("process").startEvent();
    final AbstractFlowNodeBuilder<?, ?> jobWorkerElementBuilder =
        elementBuilder.build(processBuilder).id("task");

    elementModifier.accept((ZeebeJobWorkerElementBuilder<?>) jobWorkerElementBuilder);

    return jobWorkerElementBuilder.done();
  }

  private static Stream<BpmnElementBuilder> jobWorkerElementBuilderProvider() {
    return Stream.of(
        BpmnElementBuilder.of("serviceTask", AbstractFlowNodeBuilder::serviceTask),
        BpmnElementBuilder.of("sendTask", AbstractFlowNodeBuilder::sendTask),
        BpmnElementBuilder.of(
            "message end event",
            process ->
                process.endEvent("message", AbstractThrowEventBuilder::messageEventDefinition)),
        BpmnElementBuilder.of(
            "intermediate message throw event",
            process ->
                process.intermediateThrowEvent(
                    "message", AbstractThrowEventBuilder::messageEventDefinition)));
  }
}

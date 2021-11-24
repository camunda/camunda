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
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class BusinessRuleTaskValidationTest {

  @Test
  void emptyDecisionId() {
    // when
    final BpmnModelInstance process =
        process(task -> task.zeebeCalledDecisionId("").zeebeResultVariable("result"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            ZeebeCalledDecision.class, "Attribute 'decisionId' must be present and not empty"));
  }

  @Test
  void emptyDecisionIdExpression() {
    // when
    final BpmnModelInstance process =
        process(task -> task.zeebeCalledDecisionIdExpression("").zeebeResultVariable("result"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            ZeebeCalledDecision.class, "Attribute 'decisionId' must be present and not empty"));
  }

  @Test
  void emptyResultVariable() {
    // when
    final BpmnModelInstance process =
        process(task -> task.zeebeCalledDecisionId("decisionId").zeebeResultVariable(""));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            ZeebeCalledDecision.class, "Attribute 'resultVariable' must be present and not empty"));
  }

  @Test
  void emptyJobType() {
    // when
    final BpmnModelInstance process = process(task -> task.zeebeJobType(""));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskDefinition.class, "Attribute 'type' must be present and not empty"));
  }

  @Test
  void noCalledDecisionAndTaskDefinitionExtension() {
    // when
    final BpmnModelInstance process = process(task -> {});

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            BusinessRuleTask.class,
            "Must have either one 'zeebe:calledDecision' or one 'zeebe:taskDefinition' extension element"));
  }

  @Test
  void bothCalledDecisionAndTaskDefinitionExtension() {
    // when
    final BpmnModelInstance process =
        process(
            task ->
                task.zeebeCalledDecisionId("decisionId")
                    .zeebeResultVariable("result")
                    .zeebeJobType("jobType"));

    // then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        ExpectedValidationResult.expect(
            BusinessRuleTask.class,
            "Must have either one 'zeebe:calledDecision' or one 'zeebe:taskDefinition' extension element"));
  }

  private BpmnModelInstance process(final Consumer<BusinessRuleTaskBuilder> taskBuilder) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .businessRuleTask("task", taskBuilder)
        .done();
  }
}

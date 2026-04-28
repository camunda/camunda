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
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ZeebeJobPriorityDefinitionValidationTest {

  @Test
  void shouldBeValidWithJobPriorityDefinitionOnBothProcessAndServiceTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriority("10")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriority("90"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  ////////////////////////////////////////////////////////////////////////////
  // Process-level
  ////////////////////////////////////////////////////////////////////////////

  @ParameterizedTest
  @ValueSource(strings = {"0", "42", "-100", "-2147483648", "2147483647"})
  void shouldBeValidWithLiteralPriorityOnProcess(final String priority) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriority(priority)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldBeValidWithFeelExpressionOnProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriorityExpression("processPriority")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @ValueSource(strings = {"high", "not-a-number", "2147483648", "-2147483649"})
  void shouldRejectInvalidLiteralOnProcess(final String priority) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriority(priority)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeJobPriorityDefinition.class,
            "Expected 'priority' to be a literal integer or a FEEL expression (starting with '='), but got '"
                + priority
                + "'."));
  }

  @Test
  void shouldRejectDuplicatedJobPriorityDefinitionOnProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeJobPriorityDefinitionValidationTest.duplicatedProcess.bpmn"));

    // when / then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Process.class, "Must have exactly one 'zeebe:jobPriorityDefinition'"));
  }

  ////////////////////////////////////////////////////////////////////////////
  // Task-level
  ////////////////////////////////////////////////////////////////////////////

  @ParameterizedTest
  @ValueSource(strings = {"0", "42", "-100", "-2147483648", "2147483647"})
  void shouldBeValidWithLiteralPriorityOnServiceTask(final String priority) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriority(priority))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void shouldBeValidWithFeelExpressionOnServiceTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriorityExpression("priority"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @ParameterizedTest
  @ValueSource(strings = {"high", "not-a-number", "2147483648", "-2147483649"})
  void shouldRejectInvalidLiteralOnServiceTask(final String priority) {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriority(priority))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeJobPriorityDefinition.class,
            "Expected 'priority' to be a literal integer or a FEEL expression (starting with '='), but got '"
                + priority
                + "'."));
  }

  @Test
  void shouldRejectDuplicatedJobPriorityDefinitionOnServiceTask() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeJobPriorityDefinitionValidationTest.duplicatedServiceTask.bpmn"));

    // when / then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ServiceTask.class, "Must have exactly one 'zeebe:jobPriorityDefinition'"));
  }

  @Test
  void shouldBeValidWithNoExplicitPriorityAttribute() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeJobPriorityDefinitionValidationTest.noExplicitPriority.bpmn"));

    // when / then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }
}

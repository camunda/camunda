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
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentType;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.Test;

class AgentDefinitionValidatorTest {

  @Test
  void aiAgentSubProcessOnAdHocSubProcessIsValid() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> adHocSubProcess.zeebeAiAgentSubProcessDefinition().task("A"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void aiAgentTaskOnServiceTaskIsValid() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test").zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void externalOnServiceTaskIsValid() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("test").zeebeExternalAgentDefinition())
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  void aiAgentSubProcessOnServiceTaskIsInvalid() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeAgentDefinition(ZeebeAgentType.aiAgentSubProcess))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ServiceTask.class,
            "agentType 'aiAgentSubProcess' is only allowed on an ad-hoc sub-process."));
  }

  @Test
  void aiAgentTaskOnAdHocSubProcessIsInvalid() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/AgentDefinitionValidatorTest.aiAgentTaskOnAdHocSubProcessIsInvalid.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            AdHocSubProcess.class, "agentType 'aiAgentTask' is only allowed on a service task."));
  }

  @Test
  void externalOnAdHocSubProcessIsInvalid() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/AgentDefinitionValidatorTest.externalOnAdHocSubProcessIsInvalid.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(AdHocSubProcess.class, "agentType 'external' is only allowed on a service task."));
  }

  @Test
  void missingAgentTypeIsInvalid() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/AgentDefinitionValidatorTest.missingAgentTypeIsInvalid.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeAgentDefinition.class, "Attribute 'agentType' must be present and not empty"));
  }
}

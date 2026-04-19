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
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ZeebeJobPriorityDefinitionValidationTest {

  private static final String FIXTURE_DIR = "io/camunda/zeebe/model/bpmn/validation/";

  @Test
  @DisplayName("service task with a single <zeebe:jobPriorityDefinition> is valid")
  void serviceTaskWithSinglePriorityDefinitionIsValid() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type").zeebeJobPriority("90"))
            .endEvent()
            .done();

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("process with a single <zeebe:jobPriorityDefinition> is valid")
  void processWithSinglePriorityDefinitionIsValid() {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .zeebeJobPriority("10")
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("type"))
            .endEvent()
            .done();

    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("service task with duplicated <zeebe:jobPriorityDefinition> is invalid")
  void duplicatedServiceTaskPriorityDefinitionIsInvalid() {
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                FIXTURE_DIR
                    + "ZeebeJobPriorityDefinitionValidationTest.duplicatedServiceTask.bpmn"));

    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ServiceTask.class, "Must have exactly one 'zeebe:jobPriorityDefinition'"));
  }

  @Test
  @DisplayName("process with duplicated <zeebe:jobPriorityDefinition> is invalid")
  void duplicatedProcessPriorityDefinitionIsInvalid() {
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                FIXTURE_DIR + "ZeebeJobPriorityDefinitionValidationTest.duplicatedProcess.bpmn"));

    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(Process.class, "Must have exactly one 'zeebe:jobPriorityDefinition'"));
  }
}

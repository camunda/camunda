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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListeners;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ZeebeExecutionListenersValidationTest {
  @Test
  @DisplayName("element with ExecutionListeners defined without job `type`")
  void testJobTypeNotDefined() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .zeebeJobRetries("6")
                        .zeebeStartExecutionListener(null /*job type not defined*/))
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeExecutionListener.class, "Attribute 'type' must be present and not empty"));
  }

  @Test
  @DisplayName("element with ExecutionListeners defined without `eventType`")
  void testEventTypeNotDefined() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeExecutionListenersValidationTest.testEventTypeNotDefined.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process, expect(ZeebeExecutionListener.class, "Attribute 'eventType' must be present"));
  }

  @Test
  @DisplayName("validate execution listeners are supported only for specified BPMN elements")
  void validateExecutionListenersSupportedOnlyForSpecifiedElements() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeExecutionListenersValidationTest.testElementThatNotSupportExecutionListeners.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeExecutionListeners.class,
            "Execution listeners are not supported for the 'scriptTask' element. Currently, only [serviceTask] elements can have execution listeners."));
  }

  @Test
  @DisplayName(
      "element with ExecutionListeners defined with the same `type` but different `eventType`")
  void testExecutionListenersTheSameJobTypeButDifferentEventType() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .zeebeStartExecutionListener("type_A")
                        .zeebeEndExecutionListener("type_A"))
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }

  @Test
  @DisplayName("element with ExecutionListeners defined with the same `eventType` and `type`")
  void testExecutionListenersWithTheSameEventTypeAndJobType() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                task ->
                    task.zeebeJobType("service_task_type")
                        .zeebeJobRetries("6")
                        .zeebeStartExecutionListener("type_A")
                        .zeebeStartExecutionListener("type_A")
                        .zeebeEndExecutionListener("type_A")
                        .zeebeEndExecutionListener("type_b") // unique
                        .zeebeEndExecutionListener("type_B")
                        .zeebeEndExecutionListener("type_B")
                        .zeebeEndExecutionListener("type_B")
                        .zeebeEndExecutionListener(null)
                        .zeebeEndExecutionListener(null))
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeExecutionListeners.class,
            "Found '3' duplicates based on eventType[end] and type[type_B], these combinations should be unique."),
        expect(
            ZeebeExecutionListeners.class,
            "Found '2' duplicates based on eventType[end] and type[null], these combinations should be unique."),
        expect(
            ZeebeExecutionListeners.class,
            "Found '2' duplicates based on eventType[start] and type[type_A], these combinations should be unique."),
        expect(ZeebeExecutionListener.class, "Attribute 'type' must be present and not empty"));
  }
}

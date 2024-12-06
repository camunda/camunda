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
import io.camunda.zeebe.model.bpmn.builder.TaskListenerBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ZeebeTaskListenersValidationTest {

  @Test
  @DisplayName("task listener with not defined `type` property")
  void testTaskListenerTypeNotDefined() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "my_user_task",
                ut -> ut.zeebeUserTask().zeebeTaskListener(TaskListenerBuilder::complete))
            .endEvent()
            .done();

    // when/then
    // as we don't fully support TL yet, two violations are expected here.
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskListener.class, "Attribute 'type' must be present and not empty"),
        expect(
            ZeebeTaskListener.class,
            "Task listeners are not yet supported. Java BPMN-modeling API for task"
                + " listeners was introduced with version 8.6, but the support for listener execution will "
                + "be added in the upcoming versions."));
  }

  @Test
  @DisplayName("task listener with not defined `eventType` property")
  void testEventTypeNotDefined() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeTaskListenersValidationTest.testEventTypeNotDefined.bpmn"));

    // when/then
    // as we don't fully support TL yet, two violations are expected here.
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeTaskListener.class, "Attribute 'eventType' must be present and not empty"),
        expect(
            ZeebeTaskListener.class,
            "Task listeners are not yet supported. Java BPMN-modeling API for task"
                + " listeners was introduced with version 8.6, but the support for listener execution will "
                + "be added in the upcoming versions."));
  }

  @Test
  @DisplayName("task listener with unsupported `eventType` property")
  void testEventTypesNotSupported() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "user_task",
                ut ->
                    ut.zeebeUserTask()
                        .zeebeTaskListener(
                            l ->
                                l.eventType(ZeebeTaskListenerEventType.assignment)
                                    .type("not_supported_listener")))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(
            ZeebeTaskListener.class,
            "Task listeners are not yet supported. Java BPMN-modeling API for task"
                + " listeners was introduced with version 8.6, but the support for listener execution will "
                + "be added in the upcoming versions."));
  }
}

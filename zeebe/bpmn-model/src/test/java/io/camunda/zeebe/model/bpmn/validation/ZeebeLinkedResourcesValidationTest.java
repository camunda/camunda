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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResource;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.jupiter.api.Test;

public class ZeebeLinkedResourcesValidationTest {

  @Test
  void testLinkedResourceTypeNotDefined() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "my_service_task",
                s ->
                    s.zeebeLinkedResources(
                        l -> l.bindingType(ZeebeBindingType.deployment).resourceType("RPA"))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    // when/then
    ProcessValidationUtil.assertThatProcessHasViolations(
        process,
        expect(ZeebeLinkedResource.class, "Attribute 'resourceId' must be present and not empty"));
  }

  @Test
  void testEventSuccessful() {
    // given
    final BpmnModelInstance process =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream(
                "io/camunda/zeebe/model/bpmn/validation/ZeebeLinkedResourcesValidationTest.testEvent.bpmn"));

    // when/then
    ProcessValidationUtil.assertThatProcessIsValid(process);
  }
}

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
package io.camunda.zeebe.model.bpmn.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeError;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

class BoundaryEventBuilderTest {

  private static final String PROCESS_ID = "wf";
  private static final String JOB_TYPE = "test";
  private static final String ERROR_CODE = "ERROR";

  @Test
  void testErrorBoundaryEventErrorCodeVariableCanBeSet() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent(
                "error-boundary-event",
                b -> b.errorEventDefinition().error(ERROR_CODE).errorCodeVariable("errorCode"))
            .endEvent()
            .done();

    final ModelElementInstance errorEventDefinition =
        instance.getModelElementsByType(ErrorEventDefinition.class).iterator().next();
    final ExtensionElements extensionElements =
        (ExtensionElements)
            errorEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebeError.class))
        .hasSize(1)
        .extracting(ZeebeError::getErrorCodeVariable)
        .containsExactly("errorCode");
  }

  @Test
  void testErrorBoundaryEventErrorMessageVariableCanBeSet() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent(
                "error-boundary-event",
                b ->
                    b.errorEventDefinition().error(ERROR_CODE).errorMessageVariable("errorMessage"))
            .endEvent()
            .done();

    final ModelElementInstance errorEventDefinition =
        instance.getModelElementsByType(ErrorEventDefinition.class).iterator().next();
    final ExtensionElements extensionElements =
        (ExtensionElements)
            errorEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebeError.class))
        .hasSize(1)
        .extracting(ZeebeError::getErrorMessageVariable)
        .containsExactly("errorMessage");
  }

  @Test
  void testErrorBoundaryEventErrorCodeVariableAndErrorMessageVariableCanAllBeSet() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .boundaryEvent(
                "error-boundary-event",
                b ->
                    b.errorEventDefinition()
                        .error(ERROR_CODE)
                        .errorCodeVariable("errorCode")
                        .errorMessageVariable("errorMessage"))
            .endEvent()
            .done();

    final ModelElementInstance errorEventDefinition =
        instance.getModelElementsByType(ErrorEventDefinition.class).iterator().next();
    final ExtensionElements extensionElements =
        (ExtensionElements)
            errorEventDefinition.getUniqueChildElementByType(ExtensionElements.class);

    assertThat(extensionElements.getChildElementsByType(ZeebeError.class))
        .hasSize(1)
        .extracting(ZeebeError::getErrorCodeVariable, ZeebeError::getErrorMessageVariable)
        .containsExactly(tuple("errorCode", "errorMessage"));
  }
}

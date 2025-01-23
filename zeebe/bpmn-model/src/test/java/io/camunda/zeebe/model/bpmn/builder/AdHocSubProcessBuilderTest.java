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

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

class AdHocSubProcessBuilderTest {

  @Test
  void shouldAddElementsToAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                subprocess -> {
                  subprocess.task("A");
                  subprocess.task("B");
                })
            .endEvent()
            .done();

    // when/then
    final ModelElementInstance adHocSubProcess = process.getModelElementById("ad-hoc");

    assertThat(adHocSubProcess).isInstanceOf(AdHocSubProcess.class);

    assertThat(adHocSubProcess.getChildElementsByType(FlowElement.class))
        .hasSize(2)
        .extracting(FlowElement::getId)
        .contains("A", "B");
  }
}

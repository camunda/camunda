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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;

public class ZeebeStageTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(BpmnModelConstants.ZEEBE_NS, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Collections.emptyList();
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Collections.singletonList(
        new AttributeAssumption(BpmnModelConstants.ZEEBE_NS, "alternative", false, false, false));
  }

  @Test
  public void shouldRoundTripStageOnSubProcess() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess("stage", subProcess -> subProcess.zeebeStage())
            .endEvent()
            .done();

    // when
    final SubProcess subProcess = roundTrip(modelInstance).getModelElementById("stage");

    // then
    assertThat(subProcess.getSingleExtensionElement(ZeebeStage.class)).isNotNull();
  }

  @Test
  public void shouldRoundTripStageOnAdHocSubProcess() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "stage",
                adHocSubProcess -> {
                  adHocSubProcess.zeebeStage();
                  adHocSubProcess.task("task");
                })
            .endEvent()
            .done();

    // when
    final SubProcess subProcess = roundTrip(modelInstance).getModelElementById("stage");

    // then
    assertThat(subProcess.getSingleExtensionElement(ZeebeStage.class)).isNotNull();
  }

  @Test
  public void shouldRoundTripStageOnEventSubProcess() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .eventSubProcess(
                "stage",
                eventSubProcess ->
                    eventSubProcess.zeebeStage().startEvent().signal("signal").endEvent())
            .startEvent()
            .endEvent()
            .done();

    // when
    final SubProcess subProcess = roundTrip(modelInstance).getModelElementById("stage");

    // then
    assertThat(subProcess.getSingleExtensionElement(ZeebeStage.class)).isNotNull();
  }

  @Test
  public void shouldParseStageFromHandWrittenXml() {
    // given
    final String modelXml =
        String.join(
            "\n",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"",
            "                  xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\"",
            "                  targetNamespace=\"http://bpmn.io/schema/bpmn\">",
            "  <bpmn:process id=\"loan-application\" isExecutable=\"true\">",
            "    <bpmn:subProcess id=\"documents-checked\" name=\"Documents checked\">",
            "      <bpmn:extensionElements>",
            "        <zeebe:stage />",
            "      </bpmn:extensionElements>",
            "    </bpmn:subProcess>",
            "  </bpmn:process>",
            "</bpmn:definitions>");

    // when
    final SubProcess subProcess =
        Bpmn.readModelFromStream(
                new ByteArrayInputStream(modelXml.getBytes(StandardCharsets.UTF_8)))
            .getModelElementById("documents-checked");

    // then
    assertThat(subProcess.getSingleExtensionElement(ZeebeStage.class)).isNotNull();
  }

  private static BpmnModelInstance roundTrip(final BpmnModelInstance modelInstance) {
    final String modelXml = Bpmn.convertToString(modelInstance);
    return Bpmn.readModelFromStream(
        new ByteArrayInputStream(modelXml.getBytes(StandardCharsets.UTF_8)));
  }
}

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
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ZeebeAgentDefinitionTest extends BpmnModelElementInstanceTest {

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
    return Arrays.asList(
        new AttributeAssumption(BpmnModelConstants.ZEEBE_NS, "agentType", false, true));
  }

  @ParameterizedTest
  @EnumSource(ZeebeAgentType.class)
  public void shouldRoundTripAgentDefinitionOnServiceTaskForAllAgentTypes(
      final ZeebeAgentType agentType) {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process-" + agentType)
            .startEvent()
            .serviceTask("task", t -> t.zeebeAgentDefinition(agentType))
            .endEvent()
            .done();
    final String modelXml = Bpmn.convertToString(modelInstance);

    // when
    final ServiceTask serviceTask =
        Bpmn.readModelFromStream(new ByteArrayInputStream(modelXml.getBytes()))
            .getModelElementById("task");
    final ZeebeAgentDefinition agentDefinition =
        serviceTask.getSingleExtensionElement(ZeebeAgentDefinition.class);

    // then
    assertThat(agentDefinition.getAgentType()).isEqualTo(agentType);
  }

  @ParameterizedTest
  @EnumSource(ZeebeAgentType.class)
  public void shouldRoundTripAgentDefinitionOnAdHocSubProcessForAllAgentTypes(
      final ZeebeAgentType agentType) {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process-" + agentType)
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> adHocSubProcess.zeebeAgentDefinition(agentType).task("inner"))
            .endEvent()
            .done();
    final String modelXml = Bpmn.convertToString(modelInstance);

    // when
    final AdHocSubProcess adHocSubProcess =
        Bpmn.readModelFromStream(new ByteArrayInputStream(modelXml.getBytes()))
            .getModelElementById("ad-hoc");
    final ZeebeAgentDefinition agentDefinition =
        adHocSubProcess.getSingleExtensionElement(ZeebeAgentDefinition.class);

    // then
    assertThat(agentDefinition.getAgentType()).isEqualTo(agentType);
  }
}

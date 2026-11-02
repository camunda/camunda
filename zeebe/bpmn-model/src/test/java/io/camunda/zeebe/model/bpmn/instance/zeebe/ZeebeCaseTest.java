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
import io.camunda.zeebe.model.bpmn.instance.Process;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;

public class ZeebeCaseTest extends BpmnModelElementInstanceTest {

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
        new AttributeAssumption(BpmnModelConstants.ZEEBE_NS, "title", false, false));
  }

  @Test
  public void shouldReadCaseFromXml() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process").zeebeCase("loanTitle").startEvent().done();
    final String modelXml = Bpmn.convertToString(modelInstance);

    // when
    final Process process =
        Bpmn.readModelFromStream(new ByteArrayInputStream(modelXml.getBytes()))
            .getModelElementById("process");
    final ZeebeCase zeebeCase = process.getSingleExtensionElement(ZeebeCase.class);

    // then
    assertThat(zeebeCase).isNotNull().extracting(ZeebeCase::getTitle).isEqualTo("loanTitle");
  }

  @Test
  public void shouldParseCaseFromHandWrittenXml() {
    // given
    final String modelXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " xmlns:zeebe=\"http://camunda.org/schema/zeebe/1.0\""
            + " targetNamespace=\"http://bpmn.io/schema/bpmn\">"
            + "<bpmn:process id=\"loan-application\" isExecutable=\"true\">"
            + "<bpmn:extensionElements><zeebe:case title=\"loanTitle\" /></bpmn:extensionElements>"
            + "</bpmn:process>"
            + "</bpmn:definitions>";

    // when
    final Process process =
        Bpmn.readModelFromStream(
                new ByteArrayInputStream(modelXml.getBytes(StandardCharsets.UTF_8)))
            .getModelElementById("loan-application");
    final ZeebeCase zeebeCase = process.getSingleExtensionElement(ZeebeCase.class);

    // then
    assertThat(zeebeCase).isNotNull().extracting(ZeebeCase::getTitle).isEqualTo("loanTitle");
  }
}

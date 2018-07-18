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

package io.zeebe.model.bpmn.instance.camunda;

import static io.zeebe.model.bpmn.BpmnTestConstants.PROCESS_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.CamundaExtensionsTest;
import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.impl.instance.ProcessImpl;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import java.util.Collection;
import org.junit.Test;

/**
 * Test to check the interoperability when changing elements and attributes with the {@link
 * BpmnModelConstants#ACTIVITI_NS}. In contrast to {@link CamundaExtensionsTest} this test uses
 * directly the get*Ns() methods to check the expected value.
 *
 * @author Ronny Br�unlich
 */
public class CompatabilityTest {

  @Test
  public void modifyingElementWithActivitiNsKeepsIt() {
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            CamundaExtensionsTest.class.getResourceAsStream(
                "CamundaExtensionsCompatabilityTest.xml"));
    final ProcessImpl process = modelInstance.getModelElementById(PROCESS_ID);
    final ExtensionElements extensionElements = process.getExtensionElements();
    final Collection<CamundaExecutionListener> listeners =
        extensionElements.getChildElementsByType(CamundaExecutionListener.class);
    final String listenerClass = "org.foo.Bar";
    for (final CamundaExecutionListener listener : listeners) {
      listener.setCamundaClass(listenerClass);
    }
    for (final CamundaExecutionListener listener : listeners) {
      assertThat(
          listener.getAttributeValueNs(BpmnModelConstants.ACTIVITI_NS, "class"), is(listenerClass));
    }
  }

  @Test
  public void modifyingAttributeWithActivitiNsKeepsIt() {
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            CamundaExtensionsTest.class.getResourceAsStream(
                "CamundaExtensionsCompatabilityTest.xml"));
    final ProcessImpl process = modelInstance.getModelElementById(PROCESS_ID);
    final String priority = "9000";
    process.setCamundaJobPriority(priority);
    process.setCamundaTaskPriority(priority);
    final Integer historyTimeToLive = 10;
    process.setCamundaHistoryTimeToLive(historyTimeToLive);
    process.setCamundaIsStartableInTasklist(false);
    process.setCamundaVersionTag("v1.0.0");
    assertThat(
        process.getAttributeValueNs(BpmnModelConstants.ACTIVITI_NS, "jobPriority"), is(priority));
    assertThat(
        process.getAttributeValueNs(BpmnModelConstants.ACTIVITI_NS, "taskPriority"), is(priority));
    assertThat(
        process.getAttributeValueNs(BpmnModelConstants.ACTIVITI_NS, "historyTimeToLive"),
        is(historyTimeToLive.toString()));
    assertThat(process.isCamundaStartableInTasklist(), is(false));
    assertThat(process.getCamundaVersionTag(), is("v1.0.0"));
  }
}

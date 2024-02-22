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

package io.camunda.zeebe.model.bpmn.instance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.impl.instance.Incoming;
import io.camunda.zeebe.model.bpmn.impl.instance.Outgoing;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;

/**
 * @author Sebastian Menski
 */
public class FlowNodeTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(FlowElement.class, true);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(Incoming.class), new ChildElementAssumption(Outgoing.class));
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return null;
  }

  @Test
  public void testUpdateIncomingOutgoingChildElements() {
    final BpmnModelInstance modelInstance =
        Bpmn.createProcess().startEvent().userTask("test").endEvent().done();

    // save current incoming and outgoing sequence flows
    final UserTask userTask = modelInstance.getModelElementById("test");
    final Collection<SequenceFlow> incoming = userTask.getIncoming();
    final Collection<SequenceFlow> outgoing = userTask.getOutgoing();

    // create a new service task
    final ServiceTask serviceTask = modelInstance.newInstance(ServiceTask.class);
    serviceTask.setId("new");

    // replace the user task with the new service task
    userTask.replaceWithElement(serviceTask);

    // assert that the new service task has the same incoming and outgoing sequence flows
    assertThat(serviceTask.getIncoming()).containsExactlyElementsOf(incoming);
    assertThat(serviceTask.getOutgoing()).containsExactlyElementsOf(outgoing);
  }
}

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

package io.zeebe.model.bpmn.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.impl.instance.Incoming;
import io.zeebe.model.bpmn.impl.instance.Outgoing;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;

/** @author Sebastian Menski */
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
    return Arrays.asList(
        new AttributeAssumption(CAMUNDA_NS, "asyncAfter", false, false, false),
        new AttributeAssumption(CAMUNDA_NS, "asyncBefore", false, false, false),
        new AttributeAssumption(CAMUNDA_NS, "exclusive", false, false, true),
        new AttributeAssumption(CAMUNDA_NS, "jobPriority"));
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

  @Test
  public void testCamundaAsyncBefore() {
    final Task task = modelInstance.newInstance(Task.class);
    assertThat(task.isCamundaAsyncBefore()).isFalse();

    task.setCamundaAsyncBefore(true);
    assertThat(task.isCamundaAsyncBefore()).isTrue();
  }

  @Test
  public void testCamundaAsyncAfter() {
    final Task task = modelInstance.newInstance(Task.class);
    assertThat(task.isCamundaAsyncAfter()).isFalse();

    task.setCamundaAsyncAfter(true);
    assertThat(task.isCamundaAsyncAfter()).isTrue();
  }

  @Test
  public void testCamundaAsyncAfterAndBefore() {
    final Task task = modelInstance.newInstance(Task.class);

    assertThat(task.isCamundaAsyncAfter()).isFalse();
    assertThat(task.isCamundaAsyncBefore()).isFalse();

    task.setCamundaAsyncBefore(true);

    assertThat(task.isCamundaAsyncAfter()).isFalse();
    assertThat(task.isCamundaAsyncBefore()).isTrue();

    task.setCamundaAsyncAfter(true);

    assertThat(task.isCamundaAsyncAfter()).isTrue();
    assertThat(task.isCamundaAsyncBefore()).isTrue();

    task.setCamundaAsyncBefore(false);

    assertThat(task.isCamundaAsyncAfter()).isTrue();
    assertThat(task.isCamundaAsyncBefore()).isFalse();
  }

  @Test
  public void testCamundaExclusive() {
    final Task task = modelInstance.newInstance(Task.class);

    assertThat(task.isCamundaExclusive()).isTrue();

    task.setCamundaExclusive(false);

    assertThat(task.isCamundaExclusive()).isFalse();
  }

  @Test
  public void testCamundaJobPriority() {
    final Task task = modelInstance.newInstance(Task.class);
    assertThat(task.getCamundaJobPriority()).isNull();

    task.setCamundaJobPriority("15");

    assertThat(task.getCamundaJobPriority()).isEqualTo("15");
  }
}

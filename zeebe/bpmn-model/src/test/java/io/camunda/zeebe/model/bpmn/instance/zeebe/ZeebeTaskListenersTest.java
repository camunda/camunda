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

import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListener.DEFAULT_RETRIES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstanceTest;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Test;

public class ZeebeTaskListenersTest extends BpmnModelElementInstanceTest {

  @Override
  public TypeAssumption getTypeAssumption() {
    return new TypeAssumption(BpmnModelConstants.ZEEBE_NS, false);
  }

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(BpmnModelConstants.ZEEBE_NS, ZeebeTaskListener.class));
  }

  @Override
  public Collection<AttributeAssumption> getAttributesAssumptions() {
    return Collections.emptyList();
  }

  @Test
  public void shouldReadTaskListenerElements() {
    // given
    modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "my_user_task",
                t ->
                    t.zeebeUserTask()
                        .zeebeTaskListener(l -> l.create().type("create_listener").retries("2"))
                        .zeebeTaskListener(l -> l.update().type("update_listener"))
                        .zeebeTaskListener(l -> l.update().type("update_listener_2").retries("33"))
                        .zeebeTaskListener(
                            l -> l.assignment().type("assignment_listener").retries("4"))
                        .zeebeTaskListener(l -> l.complete().type("complete_listener").retries("5"))
                        .zeebeTaskListener(l -> l.cancel().type("cancel_listener").retries("6")))
            .endEvent()
            .done();

    final ModelElementInstance userTask = modelInstance.getModelElementById("my_user_task");

    // when
    final Collection<ZeebeTaskListener> taskListeners = getTaskListeners(userTask);

    // then
    assertThat(taskListeners)
        .extracting("eventType", "type", "retries")
        .containsExactly(
            tuple(ZeebeTaskListenerEventType.create, "create_listener", "2"),
            tuple(ZeebeTaskListenerEventType.update, "update_listener", DEFAULT_RETRIES),
            tuple(ZeebeTaskListenerEventType.update, "update_listener_2", "33"),
            tuple(ZeebeTaskListenerEventType.assignment, "assignment_listener", "4"),
            tuple(ZeebeTaskListenerEventType.complete, "complete_listener", "5"),
            tuple(ZeebeTaskListenerEventType.cancel, "cancel_listener", "6"));
  }

  private Collection<ZeebeTaskListener> getTaskListeners(
      final ModelElementInstance elementInstance) {
    return elementInstance
        .getUniqueChildElementByType(ExtensionElements.class)
        .getUniqueChildElementByType(ZeebeTaskListeners.class)
        .getChildElementsByType(ZeebeTaskListener.class);
  }
}

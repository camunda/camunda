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
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskSchedule;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.util.Collection;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.jupiter.api.Test;

class UserTaskBuilderTest {

  @Test
  void testUserTaskAssigneeCanBeSet() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1", task -> task.zeebeAssignee("user1"))
            .endEvent()
            .done();

    final ModelElementInstance userTask = instance.getModelElementById("userTask1");
    final ExtensionElements extensionElements =
        (ExtensionElements) userTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeAssignmentDefinition.class))
        .hasSize(1)
        .extracting(ZeebeAssignmentDefinition::getAssignee)
        .containsExactly("user1");
  }

  @Test
  void testUserTaskCandidateGroupsCanBeSet() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1", task -> task.zeebeCandidateGroups("role1"))
            .endEvent()
            .done();

    final ModelElementInstance userTask = instance.getModelElementById("userTask1");
    final ExtensionElements extensionElements =
        (ExtensionElements) userTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeAssignmentDefinition.class))
        .hasSize(1)
        .extracting(ZeebeAssignmentDefinition::getCandidateGroups)
        .containsExactly("role1");
  }

  @Test
  void testUserTaskCandidateUsersCanBeSet() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1", task -> task.zeebeCandidateUsers("user1"))
            .endEvent()
            .done();

    final ModelElementInstance userTask = instance.getModelElementById("userTask1");
    final ExtensionElements extensionElements =
        (ExtensionElements) userTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeAssignmentDefinition.class))
        .hasSize(1)
        .extracting(ZeebeAssignmentDefinition::getCandidateUsers)
        .containsExactly("user1");
  }

  @Test
  void shouldSetDueDateOnUserTask() {
    final String dueDate = "2023-02-24T14:29:00Z";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1", task -> task.zeebeDueDate(dueDate))
            .endEvent()
            .done();

    final ModelElementInstance userTask = instance.getModelElementById("userTask1");
    final ExtensionElements extensionElements =
        (ExtensionElements) userTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeTaskSchedule.class))
        .hasSize(1)
        .extracting(ZeebeTaskSchedule::getDueDate)
        .containsExactly(dueDate);
  }

  @Test
  void shouldSetFollowUpDateOnUserTask() {
    final String followUpDate = "2023-02-24T14:29:00Z";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1", task -> task.zeebeFollowUpDate(followUpDate))
            .endEvent()
            .done();

    final ModelElementInstance userTask = instance.getModelElementById("userTask1");
    final ExtensionElements extensionElements =
        (ExtensionElements) userTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeTaskSchedule.class))
        .hasSize(1)
        .extracting(ZeebeTaskSchedule::getFollowUpDate)
        .containsExactly(followUpDate);
  }

  @Test
  void shouldSetAllExistingUserTaskProperties() {
    final String dueDate = "2023-02-24T14:29:00Z";
    final String followUpDate = "2023-02-24T14:29:00Z";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask(
                "userTask1",
                b ->
                    b.zeebeAssignee("user1")
                        .zeebeCandidateGroups("role1")
                        .zeebeCandidateUsers("user2"))
            .zeebeDueDate(dueDate)
            .zeebeFollowUpDate(followUpDate)
            .endEvent()
            .done();

    final ModelElementInstance userTask = instance.getModelElementById("userTask1");
    final ExtensionElements extensionElements =
        (ExtensionElements) userTask.getUniqueChildElementByType(ExtensionElements.class);
    assertThat(extensionElements.getChildElementsByType(ZeebeAssignmentDefinition.class))
        .hasSize(1)
        .extracting(
            ZeebeAssignmentDefinition::getAssignee,
            ZeebeAssignmentDefinition::getCandidateGroups,
            ZeebeAssignmentDefinition::getCandidateUsers)
        .containsExactly(tuple("user1", "role1", "user2"));
    assertThat(extensionElements.getChildElementsByType(ZeebeTaskSchedule.class))
        .hasSize(1)
        .extracting(ZeebeTaskSchedule::getDueDate, ZeebeTaskSchedule::getFollowUpDate)
        .containsExactly(tuple(dueDate, followUpDate));
  }

  @Test
  void testUserTaskFormIdNotNull() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .userTask("userTask1")
            .zeebeUserTaskForm("{}")
            .endEvent()
            .done();

    final Collection<ZeebeUserTaskForm> zeebeUserTaskForms =
        instance.getModelElementsByType(ZeebeUserTaskForm.class);

    assertThat(zeebeUserTaskForms).hasSize(1);
    final ZeebeUserTaskForm zeebeUserTaskForm = zeebeUserTaskForms.iterator().next();
    assertThat(zeebeUserTaskForm.getId()).isNotEmpty();
  }
}

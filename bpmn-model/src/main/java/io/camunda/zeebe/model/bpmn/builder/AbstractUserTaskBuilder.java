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

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.USER_TASK_FORM_KEY_BPMN_LOCATION;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskSchedule;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;

/**
 * @author Sebastian Menski
 */
public abstract class AbstractUserTaskBuilder<B extends AbstractUserTaskBuilder<B>>
    extends AbstractTaskBuilder<B, UserTask> implements ZeebeUserTaskPropertiesBuilder<B> {

  protected AbstractUserTaskBuilder(
      final BpmnModelInstance modelInstance, final UserTask element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the build user task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(final String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  @Override
  public B zeebeFormKey(final String format, final String location, final String id) {
    return zeebeFormKey(String.format("%s:%s:%s", format, location, id));
  }

  @Override
  public B zeebeFormKey(final String formKey) {
    final ZeebeFormDefinition formDefinition =
        getCreateSingleExtensionElement(ZeebeFormDefinition.class);
    formDefinition.setFormKey(formKey);
    return myself;
  }

  @Override
  public B zeebeUserTaskForm(final String userTaskForm) {
    final ZeebeUserTaskForm zeebeUserTaskForm = createZeebeUserTaskForm();
    zeebeUserTaskForm.setTextContent(userTaskForm);
    return zeebeFormKey(
        USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT,
        USER_TASK_FORM_KEY_BPMN_LOCATION,
        zeebeUserTaskForm.getId());
  }

  @Override
  public B zeebeUserTaskForm(final String id, final String userTaskForm) {
    final ZeebeUserTaskForm zeebeUserTaskForm = createZeebeUserTaskForm();
    zeebeUserTaskForm.setId(id);
    zeebeUserTaskForm.setTextContent(userTaskForm);
    return zeebeFormKey(
        USER_TASK_FORM_KEY_CAMUNDA_FORMS_FORMAT, USER_TASK_FORM_KEY_BPMN_LOCATION, id);
  }

  @Override
  public B zeebeAssignee(final String assignee) {
    final ZeebeAssignmentDefinition assignment =
        myself.getCreateSingleExtensionElement(ZeebeAssignmentDefinition.class);
    assignment.setAssignee(assignee);
    return myself;
  }

  @Override
  public B zeebeAssigneeExpression(final String expression) {
    return zeebeAssignee(asZeebeExpression(expression));
  }

  @Override
  public B zeebeCandidateGroups(final String candidateGroups) {
    final ZeebeAssignmentDefinition assignment =
        myself.getCreateSingleExtensionElement(ZeebeAssignmentDefinition.class);
    assignment.setCandidateGroups(candidateGroups);
    return myself;
  }

  @Override
  public B zeebeCandidateGroupsExpression(final String expression) {
    return zeebeCandidateGroups(asZeebeExpression(expression));
  }

  @Override
  public B zeebeCandidateUsers(final String candidateUsers) {
    final ZeebeAssignmentDefinition assignment =
        myself.getCreateSingleExtensionElement(ZeebeAssignmentDefinition.class);
    assignment.setCandidateUsers(candidateUsers);
    return myself;
  }

  @Override
  public B zeebeCandidateUsersExpression(final String expression) {
    return zeebeCandidateUsers(asZeebeExpression(expression));
  }

  @Override
  public B zeebeDueDate(final String dueDate) {
    final ZeebeTaskSchedule taskSchedule =
        myself.getCreateSingleExtensionElement(ZeebeTaskSchedule.class);
    taskSchedule.setDueDate(dueDate);
    return myself;
  }

  @Override
  public B zeebeDueDateExpression(final String expression) {
    return zeebeDueDate(asZeebeExpression(expression));
  }

  @Override
  public B zeebeFollowUpDate(final String followUpDate) {
    final ZeebeTaskSchedule taskSchedule =
        myself.getCreateSingleExtensionElement(ZeebeTaskSchedule.class);
    taskSchedule.setFollowUpDate(followUpDate);
    return myself;
  }

  @Override
  public B zeebeFollowUpDateExpression(final String expression) {
    return zeebeFollowUpDate(asZeebeExpression(expression));
  }

  public B zeebeTaskHeader(final String key, final String value) {
    final ZeebeTaskHeaders taskHeaders = getCreateSingleExtensionElement(ZeebeTaskHeaders.class);
    final ZeebeHeader header = createChild(taskHeaders, ZeebeHeader.class);
    header.setKey(key);
    header.setValue(value);

    return myself;
  }
}

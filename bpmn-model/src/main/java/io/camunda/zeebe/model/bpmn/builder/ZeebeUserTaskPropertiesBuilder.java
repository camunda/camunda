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

/** A fluent builder for zeebe specific user task related properties. */
public interface ZeebeUserTaskPropertiesBuilder<B extends ZeebeUserTaskPropertiesBuilder<B>> {

  /**
   * Sets the form key with the format 'format:location:id' of the build user task.
   *
   * @param format the format of the reference form
   * @param location the location where the form is available
   * @param id the id of the form
   * @return the builder object
   */
  B zeebeFormKey(String format, String location, String id);

  /**
   * Sets the form key of the build user task.
   *
   * @param formKey the form key to set
   * @return the builder object
   */
  B zeebeFormKey(String formKey);

  /**
   * Creates a new user task form with the given context, assuming it is of the format camunda-forms
   * and embedded inside the diagram.
   *
   * @param userTaskForm the XML encoded user task form json in the camunda-forms format
   * @return the builder object
   */
  B zeebeUserTaskForm(String userTaskForm);

  /**
   * Creates a new user task form with the given context, assuming it is of the format camunda-forms
   * and embedded inside the diagram.
   *
   * @param id the unique identifier of the user task form element
   * @param userTaskForm the XML encoded user task form json in the camunda-forms format
   * @return the builder object
   */
  B zeebeUserTaskForm(String id, String userTaskForm);

  /**
   * Sets a static assignee for the user task
   *
   * @param assignee the assignee of the user task
   * @return the builder object
   */
  B zeebeAssignee(String assignee);

  /**
   * Sets a dynamic assignee for the user task that is retrieved from the given expression
   *
   * @param expression the expression for the assignee of the user task
   * @return the builder object
   */
  B zeebeAssigneeExpression(String expression);

  /**
   * Sets a static candidateGroups for the user task
   *
   * @param candidateGroups the candidateGroups of the user task
   * @return the builder object
   */
  B zeebeCandidateGroups(String candidateGroups);

  /**
   * Sets a dynamic candidateGroups for the user task that is retrieved from the given expression
   *
   * @param expression the expression for the candidateGroups of the user task
   * @return the builder object
   */
  B zeebeCandidateGroupsExpression(String expression);

  /**
   * Sets a static candidateUsers for the user task
   *
   * @param candidateUsers the candidateUsers of the user task
   * @return the builder object
   */
  B zeebeCandidateUsers(String candidateUsers);

  /**
   * Sets a dynamic candidateUsers for the user task that is retrieved from the given expression
   *
   * @param expression the expression for the candidateUsers of the user task
   * @return the builder object
   */
  B zeebeCandidateUsersExpression(String expression);

  /**
   * Sets a static dueDate for the user task
   *
   * @param dueDate the dueDate of the user task
   * @return the builder object
   */
  B zeebeDueDate(String dueDate);

  /**
   * Sets a dynamic dueDate for the user task that is retrieved from the given expression
   *
   * @param expression the expression for the dueDate of the user task
   * @return the builder object
   */
  B zeebeDueDateExpression(String expression);

  /**
   * Sets a static followUpDate for the user task
   *
   * @param followUpDate the followUpDate of the user task
   * @return the builder object
   */
  B zeebeFollowUpDate(String followUpDate);

  /**
   * Sets a dynamic followUpDate for the user task that is retrieved from the given expression
   *
   * @param expression the expression for the followUpDate of the user task
   * @return the builder object
   */
  B zeebeFollowUpDateExpression(String expression);
}

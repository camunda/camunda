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

package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.UserTask;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormData;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormField;
import io.zeebe.model.bpmn.instance.camunda.CamundaTaskListener;
import java.util.List;

/** @author Sebastian Menski */
public abstract class AbstractUserTaskBuilder<B extends AbstractUserTaskBuilder<B>>
    extends AbstractTaskBuilder<B, UserTask> {

  protected AbstractUserTaskBuilder(
      BpmnModelInstance modelInstance, UserTask element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the build user task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  /** camunda extensions */

  /**
   * Sets the camunda attribute assignee.
   *
   * @param camundaAssignee the assignee to set
   * @return the builder object
   */
  public B camundaAssignee(String camundaAssignee) {
    element.setCamundaAssignee(camundaAssignee);
    return myself;
  }

  /**
   * Sets the camunda candidate groups attribute.
   *
   * @param camundaCandidateGroups the candidate groups to set
   * @return the builder object
   */
  public B camundaCandidateGroups(String camundaCandidateGroups) {
    element.setCamundaCandidateGroups(camundaCandidateGroups);
    return myself;
  }

  /**
   * Sets the camunda candidate groups attribute.
   *
   * @param camundaCandidateGroups the candidate groups to set
   * @return the builder object
   */
  public B camundaCandidateGroups(List<String> camundaCandidateGroups) {
    element.setCamundaCandidateGroupsList(camundaCandidateGroups);
    return myself;
  }

  /**
   * Sets the camunda candidate users attribute.
   *
   * @param camundaCandidateUsers the candidate users to set
   * @return the builder object
   */
  public B camundaCandidateUsers(String camundaCandidateUsers) {
    element.setCamundaCandidateUsers(camundaCandidateUsers);
    return myself;
  }

  /**
   * Sets the camunda candidate users attribute.
   *
   * @param camundaCandidateUsers the candidate users to set
   * @return the builder object
   */
  public B camundaCandidateUsers(List<String> camundaCandidateUsers) {
    element.setCamundaCandidateUsersList(camundaCandidateUsers);
    return myself;
  }

  /**
   * Sets the camunda due date attribute.
   *
   * @param camundaDueDate the due date of the user task
   * @return the builder object
   */
  public B camundaDueDate(String camundaDueDate) {
    element.setCamundaDueDate(camundaDueDate);
    return myself;
  }

  /**
   * Sets the camunda follow up date attribute.
   *
   * @param camundaFollowUpDate the follow up date of the user task
   * @return the builder object
   */
  public B camundaFollowUpDate(String camundaFollowUpDate) {
    element.setCamundaFollowUpDate(camundaFollowUpDate);
    return myself;
  }

  /**
   * Sets the camunda form handler class attribute.
   *
   * @param camundaFormHandlerClass the class name of the form handler
   * @return the builder object
   */
  @SuppressWarnings("rawtypes")
  public B camundaFormHandlerClass(Class camundaFormHandlerClass) {
    return camundaFormHandlerClass(camundaFormHandlerClass.getName());
  }

  /**
   * Sets the camunda form handler class attribute.
   *
   * @param camundaFormHandlerClass the class name of the form handler
   * @return the builder object
   */
  public B camundaFormHandlerClass(String fullQualifiedClassName) {
    element.setCamundaFormHandlerClass(fullQualifiedClassName);
    return myself;
  }

  /**
   * Sets the camunda form key attribute.
   *
   * @param camundaFormKey the form key to set
   * @return the builder object
   */
  public B camundaFormKey(String camundaFormKey) {
    element.setCamundaFormKey(camundaFormKey);
    return myself;
  }

  /**
   * Sets the camunda priority attribute.
   *
   * @param camundaPriority the priority of the user task
   * @return the builder object
   */
  public B camundaPriority(String camundaPriority) {
    element.setCamundaPriority(camundaPriority);
    return myself;
  }

  /**
   * Creates a new camunda form field extension element.
   *
   * @return the builder object
   */
  public CamundaUserTaskFormFieldBuilder camundaFormField() {
    final CamundaFormData camundaFormData = getCreateSingleExtensionElement(CamundaFormData.class);
    final CamundaFormField camundaFormField = createChild(camundaFormData, CamundaFormField.class);
    return new CamundaUserTaskFormFieldBuilder(modelInstance, element, camundaFormField);
  }

  /**
   * Add a class based task listener with specified event name
   *
   * @param eventName - event names to listen to
   * @param fullQualifiedClassName - a string representing a class
   * @return the builder object
   */
  @SuppressWarnings("rawtypes")
  public B camundaTaskListenerClass(String eventName, Class listenerClass) {
    return camundaTaskListenerClass(eventName, listenerClass.getName());
  }

  /**
   * Add a class based task listener with specified event name
   *
   * @param eventName - event names to listen to
   * @param fullQualifiedClassName - a string representing a class
   * @return the builder object
   */
  public B camundaTaskListenerClass(String eventName, String fullQualifiedClassName) {
    final CamundaTaskListener executionListener = createInstance(CamundaTaskListener.class);
    executionListener.setCamundaEvent(eventName);
    executionListener.setCamundaClass(fullQualifiedClassName);

    addExtensionElement(executionListener);

    return myself;
  }

  public B camundaTaskListenerExpression(String eventName, String expression) {
    final CamundaTaskListener executionListener = createInstance(CamundaTaskListener.class);
    executionListener.setCamundaEvent(eventName);
    executionListener.setCamundaExpression(expression);

    addExtensionElement(executionListener);

    return myself;
  }

  public B camundaTaskListenerDelegateExpression(String eventName, String delegateExpression) {
    final CamundaTaskListener executionListener = createInstance(CamundaTaskListener.class);
    executionListener.setCamundaEvent(eventName);
    executionListener.setCamundaDelegateExpression(delegateExpression);

    addExtensionElement(executionListener);

    return myself;
  }
}

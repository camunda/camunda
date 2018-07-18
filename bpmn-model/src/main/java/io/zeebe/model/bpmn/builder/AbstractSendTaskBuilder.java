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
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.Operation;
import io.zeebe.model.bpmn.instance.SendTask;

/** @author Sebastian Menski */
public abstract class AbstractSendTaskBuilder<B extends AbstractSendTaskBuilder<B>>
    extends AbstractTaskBuilder<B, SendTask> {

  protected AbstractSendTaskBuilder(
      BpmnModelInstance modelInstance, SendTask element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the send task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  /**
   * Sets the message of the send task.
   *
   * @param message the message to set
   * @return the builder object
   */
  public B message(Message message) {
    element.setMessage(message);
    return myself;
  }

  /**
   * Sets the message with the given message name. If already a message with this name exists it
   * will be used, otherwise a new message is created.
   *
   * @param messageName the name of the message
   * @return the builder object
   */
  public B message(String messageName) {
    final Message message = findMessageForName(messageName);
    return message(message);
  }

  /**
   * Sets the operation of the send task.
   *
   * @param operation the operation to set
   * @return the builder object
   */
  public B operation(Operation operation) {
    element.setOperation(operation);
    return myself;
  }

  /** camunda extensions */

  /**
   * Sets the camunda class attribute.
   *
   * @param camundaClass the class name to set
   * @return the builder object
   */
  public B camundaClass(Class delegateClass) {
    return camundaClass(delegateClass.getName());
  }

  /**
   * Sets the camunda class attribute.
   *
   * @param camundaClass the class name to set
   * @return the builder object
   */
  public B camundaClass(String fullQualifiedClassName) {
    element.setCamundaClass(fullQualifiedClassName);
    return myself;
  }

  /**
   * Sets the camunda delegateExpression attribute.
   *
   * @param camundaExpression the delegateExpression to set
   * @return the builder object
   */
  public B camundaDelegateExpression(String camundaExpression) {
    element.setCamundaDelegateExpression(camundaExpression);
    return myself;
  }

  /**
   * Sets the camunda expression attribute.
   *
   * @param camundaExpression the expression to set
   * @return the builder object
   */
  public B camundaExpression(String camundaExpression) {
    element.setCamundaExpression(camundaExpression);
    return myself;
  }

  /**
   * Sets the camunda resultVariable attribute.
   *
   * @param camundaResultVariable the name of the process variable
   * @return the builder object
   */
  public B camundaResultVariable(String camundaResultVariable) {
    element.setCamundaResultVariable(camundaResultVariable);
    return myself;
  }

  /**
   * Sets the camunda topic attribute.
   *
   * @param camundaTopic the topic to set
   * @return the builder object
   */
  public B camundaTopic(String camundaTopic) {
    element.setCamundaTopic(camundaTopic);
    return myself;
  }

  /**
   * Sets the camunda type attribute.
   *
   * @param camundaType the type of the service task
   * @return the builder object
   */
  public B camundaType(String camundaType) {
    element.setCamundaType(camundaType);
    return myself;
  }

  /**
   * Set the camunda task priority attribute. The priority is only used for service tasks which have
   * as type value <code>external</code>
   *
   * @param taskPriority the task priority which should used for the external tasks
   * @return the builder object
   */
  public B camundaTaskPriority(String taskPriority) {
    element.setCamundaTaskPriority(taskPriority);
    return myself;
  }
}

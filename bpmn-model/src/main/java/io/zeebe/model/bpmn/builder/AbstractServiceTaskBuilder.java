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
import io.zeebe.model.bpmn.instance.ServiceTask;

/** @author Sebastian Menski */
public abstract class AbstractServiceTaskBuilder<B extends AbstractServiceTaskBuilder<B>>
    extends AbstractTaskBuilder<B, ServiceTask> {

  protected AbstractServiceTaskBuilder(
      BpmnModelInstance modelInstance, ServiceTask element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the build service task.
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
   * Sets the camunda class attribute.
   *
   * @param camundaClass the class name to set
   * @return the builder object
   */
  @SuppressWarnings("rawtypes")
  public B camundaClass(Class camundaClass) {
    return camundaClass(camundaClass.getName());
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
   * Sets the camunda topic attribute. This is only meaningful when the {@link #camundaType(String)}
   * attribute has the value <code>external</code>.
   *
   * @param camundaTopic the topic to set
   * @return the build object
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
   * Sets the camunda topic attribute and the camunda type attribute to the
   * value <code>external</code. Reduces two calls to {@link #camundaType(String)} and {@link #camundaTopic(String)}.
   *
   * @param camundaTopic the topic to set
   * @return the build object
   */
  public B camundaExternalTask(String camundaTopic) {
    this.camundaType("external");
    this.camundaTopic(camundaTopic);
    return myself;
  }

  /**
   * Sets the camunda task priority attribute. This is only meaningful when the {@link
   * #camundaType(String)} attribute has the value <code>external</code>.
   *
   * @param taskPriority the priority for the external task
   * @return the builder object
   */
  public B camundaTaskPriority(String taskPriority) {
    element.setCamundaTaskPriority(taskPriority);
    return myself;
  }
}

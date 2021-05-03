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
      final BpmnModelInstance modelInstance, final SendTask element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the send task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(final String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  /**
   * Sets the message of the send task.
   *
   * @param message the message to set
   * @return the builder object
   */
  public B message(final Message message) {
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
  public B message(final String messageName) {
    final Message message = findMessageForName(messageName);
    return message(message);
  }

  /**
   * Sets the operation of the send task.
   *
   * @param operation the operation to set
   * @return the builder object
   */
  public B operation(final Operation operation) {
    element.setOperation(operation);
    return myself;
  }
}

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
import io.zeebe.model.bpmn.instance.CallActivity;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;

/** @author Sebastian Menski */
public class AbstractCallActivityBuilder<B extends AbstractCallActivityBuilder<B>>
    extends AbstractActivityBuilder<B, CallActivity> {

  protected AbstractCallActivityBuilder(
      final BpmnModelInstance modelInstance, final CallActivity element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the called element
   *
   * @param calledElement the process to call
   * @return the builder object
   */
  public B calledElement(final String calledElement) {
    element.setCalledElement(calledElement);
    return myself;
  }

  public B zeebeProcessId(final String processId) {
    final ZeebeCalledElement calledElement =
        getCreateSingleExtensionElement(ZeebeCalledElement.class);
    calledElement.setProcessId(processId);
    return myself;
  }

  public B zeebeProcessIdExpression(final String processIdExpression) {
    final ZeebeCalledElement calledElement =
        getCreateSingleExtensionElement(ZeebeCalledElement.class);
    calledElement.setProcessId(asZeebeExpression(processIdExpression));
    return myself;
  }

  public B zeebePropagateAllChildVariables(final boolean propagateAllChildVariables) {
    final ZeebeCalledElement calledElement =
        getCreateSingleExtensionElement(ZeebeCalledElement.class);
    calledElement.setPropagateAllChildVariablesEnabled(propagateAllChildVariables);
    return myself;
  }
}

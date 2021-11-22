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

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;

/** @author Sebastian Menski */
public abstract class AbstractBusinessRuleTaskBuilder<B extends AbstractBusinessRuleTaskBuilder<B>>
    extends AbstractJobWorkerTaskBuilder<B, BusinessRuleTask> {

  protected AbstractBusinessRuleTaskBuilder(
      final BpmnModelInstance modelInstance,
      final BusinessRuleTask element,
      final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the business rule task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(final String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  /**
   * Sets a static id of the decision that is called.
   *
   * @param decisionId the id of the decision
   * @return the builder object
   */
  public B zeebeCalledDecisionId(final String decisionId) {
    final ZeebeCalledDecision calledDecision =
        getCreateSingleExtensionElement(ZeebeCalledDecision.class);
    calledDecision.setDecisionId(decisionId);
    return myself;
  }

  /**
   * Sets a dynamic id of the decision that is called. The id is retrieved from the given
   * expression.
   *
   * @param decisionIdExpression the expression for the id of the decision
   * @return the builder object
   */
  public B zeebeCalledDecisionIdExpression(final String decisionIdExpression) {
    return zeebeCalledDecisionId(asZeebeExpression(decisionIdExpression));
  }

  /**
   * Sets the name of the result variable.
   *
   * @param resultVariable the name of the result variable
   * @return the builder object
   */
  public B zeebeResultVariable(final String resultVariable) {
    final ZeebeCalledDecision calledDecision =
        getCreateSingleExtensionElement(ZeebeCalledDecision.class);
    calledDecision.setResultVariable(resultVariable);
    return myself;
  }
}

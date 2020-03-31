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
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.CompletionCondition;
import io.zeebe.model.bpmn.instance.LoopCardinality;
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;

/** @author Thorben Lindhauer */
public class AbstractMultiInstanceLoopCharacteristicsBuilder<
        B extends AbstractMultiInstanceLoopCharacteristicsBuilder<B>>
    extends AbstractBaseElementBuilder<B, MultiInstanceLoopCharacteristics> {

  protected AbstractMultiInstanceLoopCharacteristicsBuilder(
      final BpmnModelInstance modelInstance,
      final MultiInstanceLoopCharacteristics element,
      final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the multi instance loop characteristics to be sequential.
   *
   * @return the builder object
   */
  public B sequential() {
    element.setSequential(true);
    return myself;
  }

  /**
   * Sets the multi instance loop characteristics to be parallel.
   *
   * @return the builder object
   */
  public B parallel() {
    element.setSequential(false);
    return myself;
  }

  /**
   * Sets the cardinality expression.
   *
   * @param expression the cardinality expression
   * @return the builder object
   */
  public B cardinality(final String expression) {
    final LoopCardinality cardinality = getCreateSingleChild(LoopCardinality.class);
    cardinality.setTextContent(expression);

    return myself;
  }

  /**
   * Sets the completion condition expression.
   *
   * @param expression the completion condition expression
   * @return the builder object
   */
  public B completionCondition(final String expression) {
    final CompletionCondition condition = getCreateSingleChild(CompletionCondition.class);
    condition.setTextContent(expression);

    return myself;
  }

  /**
   * Finishes the building of a multi instance loop characteristics.
   *
   * @return the parent activity builder
   */
  public <T extends AbstractActivityBuilder> T multiInstanceDone() {
    return (T) ((Activity) element.getParentElement()).builder();
  }

  public B zeebeInputCollection(final String inputCollection) {
    final ZeebeLoopCharacteristics characteristics =
        getCreateSingleExtensionElement(ZeebeLoopCharacteristics.class);
    characteristics.setInputCollection(inputCollection);
    return myself;
  }

  public B zeebeInputCollectionExpression(final String inputCollectionExpression) {
    return zeebeInputCollection(asZeebeExpression(inputCollectionExpression));
  }

  public B zeebeInputElement(final String inputElement) {
    final ZeebeLoopCharacteristics characteristics =
        getCreateSingleExtensionElement(ZeebeLoopCharacteristics.class);
    characteristics.setInputElement(inputElement);
    return myself;
  }

  public B zeebeOutputCollection(final String outputCollection) {
    final ZeebeLoopCharacteristics characteristics =
        getCreateSingleExtensionElement(ZeebeLoopCharacteristics.class);
    characteristics.setOutputCollection(outputCollection);
    return myself;
  }

  public B zeebeOutputElement(final String outputElement) {
    final ZeebeLoopCharacteristics characteristics =
        getCreateSingleExtensionElement(ZeebeLoopCharacteristics.class);
    characteristics.setOutputElement(outputElement);
    return myself;
  }

  public B zeebeOutputElementExpression(final String outputElementExpression) {
    return zeebeOutputElement(asZeebeExpression(outputElementExpression));
  }
}

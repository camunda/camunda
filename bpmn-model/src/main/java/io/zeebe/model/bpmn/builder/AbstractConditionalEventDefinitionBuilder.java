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
import io.zeebe.model.bpmn.instance.Condition;
import io.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.zeebe.model.bpmn.instance.Event;
import java.util.List;

public class AbstractConditionalEventDefinitionBuilder<
        B extends AbstractConditionalEventDefinitionBuilder<B>>
    extends AbstractRootElementBuilder<B, ConditionalEventDefinition> {

  public AbstractConditionalEventDefinitionBuilder(
      BpmnModelInstance modelInstance, ConditionalEventDefinition element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the condition of the conditional event definition.
   *
   * @param conditionText the condition which should be evaluate to true or false
   * @return the builder object
   */
  public B condition(String conditionText) {
    final Condition condition = createInstance(Condition.class);
    condition.setTextContent(conditionText);
    element.setCondition(condition);
    return myself;
  }

  /**
   * Sets the camunda variable name attribute, that defines on which variable the condition should
   * be evaluated.
   *
   * @param variableName the variable on which the condition should be evaluated
   * @return the builder object
   */
  public B camundaVariableName(String variableName) {
    element.setCamundaVariableName(variableName);
    return myself;
  }

  /**
   * Set the camunda variable events attribute, that defines the variable event on which the
   * condition should be evaluated.
   *
   * @param variableEvents the events on which the condition should be evaluated
   * @return the builder object
   */
  public B camundaVariableEvents(String variableEvents) {
    element.setCamundaVariableEvents(variableEvents);
    return myself;
  }

  /**
   * Set the camunda variable events attribute, that defines the variable event on which the
   * condition should be evaluated.
   *
   * @param variableEvents the events on which the condition should be evaluated
   * @return the builder object
   */
  public B camundaVariableEvents(List<String> variableEvents) {
    element.setCamundaVariableEventsList(variableEvents);
    return myself;
  }

  /**
   * Finishes the building of a conditional event definition.
   *
   * @param <T>
   * @return the parent event builder
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public <T extends AbstractFlowNodeBuilder> T conditionalEventDefinitionDone() {
    return (T) ((Event) element.getParentElement()).builder();
  }
}

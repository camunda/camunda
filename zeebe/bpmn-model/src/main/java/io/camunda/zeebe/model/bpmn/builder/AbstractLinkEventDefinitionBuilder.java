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
import io.camunda.zeebe.model.bpmn.instance.Event;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;

public abstract class AbstractLinkEventDefinitionBuilder<
        B extends AbstractLinkEventDefinitionBuilder<B>>
    extends AbstractRootElementBuilder<B, LinkEventDefinition> {

  public AbstractLinkEventDefinitionBuilder(
      final BpmnModelInstance modelInstance,
      final LinkEventDefinition element,
      final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  @Override
  public B id(final String identifier) {
    return super.id(identifier);
  }

  /**
   * Sets the link attribute.
   *
   * @param name the link for the message event definition
   * @return the builder object
   */
  public B name(final String name) {
    element.setName(name);
    return myself;
  }

  /**
   * Finishes the building of a link event definition.
   *
   * @param <T>
   * @return the parent event builder
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public <T extends AbstractFlowNodeBuilder> T linkEventDefinitionDone() {
    return (T) ((Event) element.getParentElement()).builder();
  }
}

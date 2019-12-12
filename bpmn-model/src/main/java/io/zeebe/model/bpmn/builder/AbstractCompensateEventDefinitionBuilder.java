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

import io.zeebe.model.bpmn.BpmnModelException;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.Event;

public abstract class AbstractCompensateEventDefinitionBuilder<
        B extends AbstractCompensateEventDefinitionBuilder<B>>
    extends AbstractRootElementBuilder<B, CompensateEventDefinition> {

  public AbstractCompensateEventDefinitionBuilder(
      final BpmnModelInstance modelInstance,
      final CompensateEventDefinition element,
      final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  @Override
  public B id(final String identifier) {
    return super.id(identifier);
  }

  public B activityRef(final String activityId) {
    final Activity activity = modelInstance.getModelElementById(activityId);

    if (activity == null) {
      throw new BpmnModelException("Activity with id '" + activityId + "' does not exist");
    }
    final Event event = (Event) element.getParentElement();
    if (activity.getParentElement() != event.getParentElement()) {
      throw new BpmnModelException(
          "Activity with id '"
              + activityId
              + "' must be in the same scope as '"
              + event.getId()
              + "'");
    }

    element.setActivity(activity);
    return myself;
  }

  public B waitForCompletion(final boolean waitForCompletion) {
    element.setWaitForCompletion(waitForCompletion);
    return myself;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public <T extends AbstractFlowNodeBuilder> T compensateEventDefinitionDone() {
    return (T) ((Event) element.getParentElement()).builder();
  }
}

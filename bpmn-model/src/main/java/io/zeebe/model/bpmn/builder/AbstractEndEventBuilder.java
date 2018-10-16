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
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMappingType;
import io.zeebe.model.bpmn.instance.zeebe.ZeebePayloadMappings;

/** @author Sebastian Menski */
public abstract class AbstractEndEventBuilder<B extends AbstractEndEventBuilder<B>>
    extends AbstractThrowEventBuilder<B, EndEvent> {

  protected AbstractEndEventBuilder(
      BpmnModelInstance modelInstance, EndEvent element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets an error definition for the given error code. If already an error with this code exists it
   * will be used, otherwise a new error is created.
   *
   * @param errorCode the code of the error
   * @return the builder object
   */
  public B error(String errorCode) {
    final ErrorEventDefinition errorEventDefinition = createErrorEventDefinition(errorCode);
    element.getEventDefinitions().add(errorEventDefinition);

    return myself;
  }

  public B payloadMapping(String source, String target) {
    return payloadMapping(source, target, ZeebeMappingType.PUT);
  }

  public B payloadMapping(String source, String target, ZeebeMappingType type) {
    final ZeebePayloadMappings mappings =
        getCreateSingleExtensionElement(ZeebePayloadMappings.class);
    final ZeebeMapping mapping = createChild(mappings, ZeebeMapping.class);
    mapping.setSource(source);
    mapping.setTarget(target);
    mapping.setType(type);

    return myself;
  }
}

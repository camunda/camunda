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
import io.zeebe.model.bpmn.EventBasedGatewayType;
import io.zeebe.model.bpmn.instance.EventBasedGateway;

/** @author Sebastian Menski */
public class AbstractEventBasedGatewayBuilder<B extends AbstractEventBasedGatewayBuilder<B>>
    extends AbstractGatewayBuilder<B, EventBasedGateway> {

  protected AbstractEventBasedGatewayBuilder(
      final BpmnModelInstance modelInstance,
      final EventBasedGateway element,
      final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the build event based gateway to be instantiate.
   *
   * @return the builder object
   */
  public B instantiate() {
    element.setInstantiate(true);
    return myself;
  }

  /**
   * Sets the event gateway type of the build event based gateway.
   *
   * @param eventGatewayType the event gateway type to set
   * @return the builder object
   */
  public B eventGatewayType(final EventBasedGatewayType eventGatewayType) {
    element.setEventGatewayType(eventGatewayType);
    return myself;
  }
}

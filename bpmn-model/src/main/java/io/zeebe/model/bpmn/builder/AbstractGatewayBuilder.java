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
import io.zeebe.model.bpmn.GatewayDirection;
import io.zeebe.model.bpmn.instance.Gateway;

/** @author Sebastian Menski */
public abstract class AbstractGatewayBuilder<
        B extends AbstractGatewayBuilder<B, E>, E extends Gateway>
    extends AbstractFlowNodeBuilder<B, E> {

  protected AbstractGatewayBuilder(
      final BpmnModelInstance modelInstance, final E element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the direction of the gateway build.
   *
   * @param gatewayDirection the direction to set
   * @return the builder object
   */
  public B gatewayDirection(final GatewayDirection gatewayDirection) {
    element.setGatewayDirection(gatewayDirection);
    return myself;
  }
}

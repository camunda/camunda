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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_GATEWAY_DIRECTION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_GATEWAY;

import io.zeebe.model.bpmn.GatewayDirection;
import io.zeebe.model.bpmn.builder.AbstractGatewayBuilder;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Gateway;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN gateway element
 *
 * @author Sebastian Menski
 */
public abstract class GatewayImpl extends FlowNodeImpl implements Gateway {

  protected static Attribute<GatewayDirection> gatewayDirectionAttribute;

  public GatewayImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Gateway.class, BPMN_ELEMENT_GATEWAY)
            .namespaceUri(BPMN20_NS)
            .extendsType(FlowNode.class)
            .abstractType();

    gatewayDirectionAttribute =
        typeBuilder
            .enumAttribute(BPMN_ATTRIBUTE_GATEWAY_DIRECTION, GatewayDirection.class)
            .defaultValue(GatewayDirection.Unspecified)
            .build();

    typeBuilder.build();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public abstract AbstractGatewayBuilder builder();

  @Override
  public GatewayDirection getGatewayDirection() {
    return gatewayDirectionAttribute.getValue(this);
  }

  @Override
  public void setGatewayDirection(final GatewayDirection gatewayDirection) {
    gatewayDirectionAttribute.setValue(this, gatewayDirection);
  }

  @Override
  public BpmnShape getDiagramElement() {
    return (BpmnShape) super.getDiagramElement();
  }
}

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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_EVENT_GATEWAY_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_INSTANTIATE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_EVENT_BASED_GATEWAY;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.EventBasedGatewayType;
import io.zeebe.model.bpmn.builder.EventBasedGatewayBuilder;
import io.zeebe.model.bpmn.instance.EventBasedGateway;
import io.zeebe.model.bpmn.instance.Gateway;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN eventBasedGateway element
 *
 * @author Sebastian Menski
 */
public class EventBasedGatewayImpl extends GatewayImpl implements EventBasedGateway {

  protected static Attribute<Boolean> instantiateAttribute;
  protected static Attribute<EventBasedGatewayType> eventGatewayTypeAttribute;

  public EventBasedGatewayImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(EventBasedGateway.class, BPMN_ELEMENT_EVENT_BASED_GATEWAY)
            .namespaceUri(BPMN20_NS)
            .extendsType(Gateway.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<EventBasedGateway>() {
                  @Override
                  public EventBasedGateway newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new EventBasedGatewayImpl(instanceContext);
                  }
                });

    instantiateAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_INSTANTIATE).defaultValue(false).build();

    eventGatewayTypeAttribute =
        typeBuilder
            .enumAttribute(BPMN_ATTRIBUTE_EVENT_GATEWAY_TYPE, EventBasedGatewayType.class)
            .defaultValue(EventBasedGatewayType.Exclusive)
            .build();

    typeBuilder.build();
  }

  @Override
  public EventBasedGatewayBuilder builder() {
    return new EventBasedGatewayBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public boolean isInstantiate() {
    return instantiateAttribute.getValue(this);
  }

  @Override
  public void setInstantiate(final boolean isInstantiate) {
    instantiateAttribute.setValue(this, isInstantiate);
  }

  @Override
  public EventBasedGatewayType getEventGatewayType() {
    return eventGatewayTypeAttribute.getValue(this);
  }

  @Override
  public void setEventGatewayType(final EventBasedGatewayType eventGatewayType) {
    eventGatewayTypeAttribute.setValue(this, eventGatewayType);
  }
}

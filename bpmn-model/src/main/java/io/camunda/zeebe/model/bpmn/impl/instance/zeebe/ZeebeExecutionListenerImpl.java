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
package io.camunda.zeebe.model.bpmn.impl.instance.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeExecutionListenerImpl extends BpmnModelElementInstanceImpl
    implements ZeebeExecutionListener {

  protected static Attribute<ZeebeExecutionListenerEventType> eventTypeAttribute;
  protected static Attribute<String> typeAttribute;
  protected static Attribute<String> retriesAttribute;

  public ZeebeExecutionListenerImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public ZeebeExecutionListenerEventType getEventType() {
    return eventTypeAttribute.getValue(this);
  }

  @Override
  public void setEventType(final ZeebeExecutionListenerEventType eventType) {
    eventTypeAttribute.setValue(this, eventType);
  }

  @Override
  public String getType() {
    return typeAttribute.getValue(this);
  }

  @Override
  public void setType(final String type) {
    typeAttribute.setValue(this, type);
  }

  @Override
  public String getRetries() {
    return retriesAttribute.getValue(this);
  }

  @Override
  public void setRetries(final String retries) {
    retriesAttribute.setValue(this, retries);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeExecutionListener.class, ZeebeConstants.ELEMENT_EXECUTION_LISTENER)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeExecutionListenerImpl::new);

    eventTypeAttribute =
        typeBuilder
            .enumAttribute(
                ZeebeConstants.ATTRIBUTE_EVENT_TYPE, ZeebeExecutionListenerEventType.class)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    typeAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_TYPE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    retriesAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_RETRIES)
            .defaultValue(ZeebeExecutionListener.DEFAULT_RETRIES)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}

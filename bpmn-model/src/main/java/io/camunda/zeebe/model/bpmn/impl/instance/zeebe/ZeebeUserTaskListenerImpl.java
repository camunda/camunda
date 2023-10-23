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

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskListener;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskListenerEventType;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeUserTaskListenerImpl extends BpmnModelElementInstanceImpl
    implements ZeebeUserTaskListener {

  private static Attribute<String> nameAttribute;

  private static Attribute<ZeebeUserTaskListenerEventType> eventTypeAttribute;

  public ZeebeUserTaskListenerImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeUserTaskListener.class, ZeebeConstants.ELEMENT_USER_TASK_LISTENER)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeUserTaskListenerImpl::new);

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    eventTypeAttribute =
        typeBuilder
            .enumAttribute(
                ZeebeConstants.USER_TASK_LISTENER_EVENT_TYPE, ZeebeUserTaskListenerEventType.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public String name() {
    return nameAttribute.getValue(this);
  }

  @Override
  public ZeebeUserTaskListenerEventType eventType() {
    return eventTypeAttribute.getValue(this);
  }

  @Override
  public void setName(final String name) {
    nameAttribute.setValue(this, name);
  }

  @Override
  public void setEventType(final ZeebeUserTaskListenerEventType eventType) {
    eventTypeAttribute.setValue(this, eventType);
  }
}

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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebePriorityDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebePriorityDefinitionImpl extends BpmnModelElementInstanceImpl
    implements ZeebePriorityDefinition {

  protected static Attribute<String> priorityAttribute;

  public ZeebePriorityDefinitionImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getPriority() {
    return priorityAttribute.getValue(this);
  }

  @Override
  public void setPriority(final String priority) {
    priorityAttribute.setValue(this, priority);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebePriorityDefinition.class, ZeebeConstants.ELEMENT_PRIORITY_DEFINITION)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebePriorityDefinitionImpl::new);

    priorityAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_PRIORITY)
            .defaultValue(ZeebePriorityDefinition.DEFAULT_PRIORITY)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}

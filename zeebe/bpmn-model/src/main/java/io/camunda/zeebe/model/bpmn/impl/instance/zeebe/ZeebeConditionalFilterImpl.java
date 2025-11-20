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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConditionalFilter;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeConditionalFilterImpl extends BpmnModelElementInstanceImpl
    implements ZeebeConditionalFilter {

  protected static Attribute<String> variableNamesAttribute;
  protected static Attribute<String> variableEventsAttribute;

  public ZeebeConditionalFilterImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getVariableNames() {
    return variableNamesAttribute.getValue(this);
  }

  @Override
  public void setVariableNames(final String variableNames) {
    variableNamesAttribute.setValue(this, variableNames);
  }

  @Override
  public String getVariableEvents() {
    return variableEventsAttribute.getValue(this);
  }

  @Override
  public void setVariableEvents(final String variableEvents) {
    variableEventsAttribute.setValue(this, variableEvents);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeConditionalFilter.class, ZeebeConstants.ELEMENT_CONDITIONAL_FILTER)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeConditionalFilterImpl::new);

    variableNamesAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_VARIABLE_NAMES)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    variableEventsAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_VARIABLE_EVENTS)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}

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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebePropertyImpl extends BpmnModelElementInstanceImpl implements ZeebeProperty {

  private static Attribute<String> nameAttribute;
  private static Attribute<String> valueAttribute;

  public ZeebePropertyImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getName() {
    return nameAttribute.getValue(this);
  }

  @Override
  public void setName(final String name) {
    nameAttribute.setValue(this, name);
  }

  @Override
  public String getValue() {
    return valueAttribute.getValue(this);
  }

  @Override
  public void setValue(final String value) {
    valueAttribute.setValue(this, value);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeProperty.class, ZeebeConstants.ELEMENT_PROPERTY)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebePropertyImpl::new);

    nameAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_NAME)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    valueAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_VALUE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    typeBuilder.build();
  }
}

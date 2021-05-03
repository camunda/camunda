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
package io.zeebe.model.bpmn.impl.instance.zeebe;

import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.impl.ZeebeConstants;
import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeHeaderImpl extends BpmnModelElementInstanceImpl implements ZeebeHeader {

  private static Attribute<String> keyAttribute;
  private static Attribute<String> valueAttribute;

  public ZeebeHeaderImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getKey() {
    return keyAttribute.getValue(this);
  }

  @Override
  public void setKey(final String key) {
    keyAttribute.setValue(this, key);
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
            .defineType(ZeebeHeader.class, ZeebeConstants.ELEMENT_HEADER)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeHeaderImpl::new);

    keyAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_KEY)
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

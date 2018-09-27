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
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeMappingType;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeMappingImpl extends BpmnModelElementInstanceImpl implements ZeebeMapping {

  private static Attribute<String> sourceAttribute;
  private static Attribute<String> targetAttribute;
  private static Attribute<ZeebeMappingType> typeAttribute;

  public ZeebeMappingImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getSource() {
    return sourceAttribute.getValue(this);
  }

  @Override
  public void setSource(String source) {
    sourceAttribute.setValue(this, source);
  }

  @Override
  public String getTarget() {
    return targetAttribute.getValue(this);
  }

  @Override
  public void setTarget(String target) {
    targetAttribute.setValue(this, target);
  }

  @Override
  public ZeebeMappingType getType() {
    return typeAttribute.getValue(this);
  }

  @Override
  public void setType(ZeebeMappingType type) {
    typeAttribute.setValue(this, type);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeMapping.class, ZeebeConstants.ELEMENT_MAPPING)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeMappingImpl::new);

    sourceAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_SOURCE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    targetAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_TARGET)
            .required()
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeAttribute =
        typeBuilder
            .enumAttribute(ZeebeConstants.ATTRIBUTE_TYPE, ZeebeMappingType.class)
            .defaultValue(ZeebeMappingType.PUT)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}

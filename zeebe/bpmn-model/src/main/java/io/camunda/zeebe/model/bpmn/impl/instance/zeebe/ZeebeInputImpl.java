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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeInputImpl extends BpmnModelElementInstanceImpl implements ZeebeInput {

  private static Attribute<String> sourceAttribute;
  private static Attribute<String> targetAttribute;

  public ZeebeInputImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getSource() {
    return sourceAttribute.getValue(this);
  }

  @Override
  public void setSource(final String source) {
    sourceAttribute.setValue(this, source);
  }

  @Override
  public String getTarget() {
    return targetAttribute.getValue(this);
  }

  @Override
  public void setTarget(final String target) {
    targetAttribute.setValue(this, target);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeInput.class, ZeebeConstants.ELEMENT_INPUT)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeInputImpl::new);

    sourceAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_SOURCE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    targetAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_TARGET)
            .required()
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}

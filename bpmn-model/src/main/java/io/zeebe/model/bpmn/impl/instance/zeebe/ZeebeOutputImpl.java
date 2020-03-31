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
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeOutputImpl extends BpmnModelElementInstanceImpl implements ZeebeOutput {

  private static Attribute<String> sourceAttribute;
  private static Attribute<String> targetAttribute;

  public ZeebeOutputImpl(final ModelTypeInstanceContext instanceContext) {
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
            .defineType(ZeebeOutput.class, ZeebeConstants.ELEMENT_OUTPUT)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeOutputImpl::new);

    sourceAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_SOURCE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    targetAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_TARGET)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    typeBuilder.build();
  }
}

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
import io.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeCalledElementImpl extends BpmnModelElementInstanceImpl
    implements ZeebeCalledElement {

  private static Attribute<String> processIdAttribute;
  private static Attribute<Boolean> propagateAllChildVariablesAttribute;

  public ZeebeCalledElementImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getProcessId() {
    return processIdAttribute.getValue(this);
  }

  @Override
  public void setProcessId(final String processId) {
    processIdAttribute.setValue(this, processId);
  }

  @Override
  public boolean isPropagateAllChildVariablesEnabled() {
    return propagateAllChildVariablesAttribute.getValue(this);
  }

  @Override
  public void setPropagateAllChildVariablesEnabled(
      final boolean propagateAllChildVariablesEnabled) {
    propagateAllChildVariablesAttribute.setValue(this, propagateAllChildVariablesEnabled);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeCalledElement.class, ZeebeConstants.ELEMENT_CALLED_ELEMENT)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeCalledElementImpl::new);

    processIdAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_PROCESS_ID)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    propagateAllChildVariablesAttribute =
        typeBuilder
            .booleanAttribute(ZeebeConstants.ATTRIBUTE_PROPAGATE_ALL_CHILD_VARIABLES)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .defaultValue(true)
            .build();

    typeBuilder.build();
  }
}

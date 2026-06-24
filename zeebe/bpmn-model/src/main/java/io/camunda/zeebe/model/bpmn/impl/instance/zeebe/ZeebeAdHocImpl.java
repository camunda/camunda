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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHoc;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeAdHocImpl extends BpmnModelElementInstanceImpl implements ZeebeAdHoc {

  private static Attribute<String> activeElementsCollectionAttribute;
  private static Attribute<String> outputCollectionAttribute;
  private static Attribute<String> outputElementAttribute;

  public ZeebeAdHocImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeAdHoc.class, ZeebeConstants.ELEMENT_AD_HOC)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeAdHocImpl::new);

    activeElementsCollectionAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_ACTIVE_ELEMENTS_COLLECTION)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    outputCollectionAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_OUTPUT_COLLECTION)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    outputElementAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_OUTPUT_ELEMENT)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }

  @Override
  public String getActiveElementsCollection() {
    return activeElementsCollectionAttribute.getValue(this);
  }

  @Override
  public void setActiveElementsCollection(final String activeElementsCollection) {
    activeElementsCollectionAttribute.setValue(this, activeElementsCollection);
  }

  @Override
  public String getOutputCollection() {
    return outputCollectionAttribute.getValue(this);
  }

  @Override
  public void setOutputCollection(final String outputCollection) {
    outputCollectionAttribute.setValue(this, outputCollection);
  }

  @Override
  public String getOutputElement() {
    return outputElementAttribute.getValue(this);
  }

  @Override
  public void setOutputElement(final String outputElement) {
    outputElementAttribute.setValue(this, outputElement);
  }
}

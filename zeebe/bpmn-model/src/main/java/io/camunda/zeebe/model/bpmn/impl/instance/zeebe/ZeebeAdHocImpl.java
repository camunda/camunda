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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAdHocImplementationType;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeAdHocImpl extends BpmnModelElementInstanceImpl implements ZeebeAdHoc {

  private static Attribute<String> activeElementsCollectionAttribute;
  private static Attribute<ZeebeAdHocImplementationType> implementationTypeAttribute;

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

    implementationTypeAttribute =
        typeBuilder
            .enumAttribute(
                BpmnModelConstants.BPMN_ATTRIBUTE_IMPLEMENTATION,
                ZeebeAdHocImplementationType.class)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .defaultValue(ZeebeAdHocImplementationType.BPMN)
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
  public ZeebeAdHocImplementationType getImplementationType() {
    return implementationTypeAttribute.getValue(this);
  }

  @Override
  public void setImplementationType(final ZeebeAdHocImplementationType implementationType) {
    implementationTypeAttribute.setValue(this, implementationType);
  }
}

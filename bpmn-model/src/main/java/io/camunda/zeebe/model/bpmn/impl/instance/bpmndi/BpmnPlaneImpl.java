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

package io.zeebe.model.bpmn.impl.instance.bpmndi;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ATTRIBUTE_BPMN_ELEMENT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ELEMENT_BPMN_PLANE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;

import io.zeebe.model.bpmn.impl.instance.di.PlaneImpl;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnPlane;
import io.zeebe.model.bpmn.instance.di.Plane;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMNDI BPMNPlane element
 *
 * @author Sebastian Menski
 */
public class BpmnPlaneImpl extends PlaneImpl implements BpmnPlane {

  protected static AttributeReference<BaseElement> bpmnElementAttribute;

  public BpmnPlaneImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BpmnPlane.class, BPMNDI_ELEMENT_BPMN_PLANE)
            .namespaceUri(BPMNDI_NS)
            .extendsType(Plane.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BpmnPlane>() {
                  @Override
                  public BpmnPlane newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new BpmnPlaneImpl(instanceContext);
                  }
                });

    bpmnElementAttribute =
        typeBuilder
            .stringAttribute(BPMNDI_ATTRIBUTE_BPMN_ELEMENT)
            .qNameAttributeReference(BaseElement.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public BaseElement getBpmnElement() {
    return bpmnElementAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setBpmnElement(final BaseElement bpmnElement) {
    bpmnElementAttribute.setReferenceTargetElement(this, bpmnElement);
  }
}

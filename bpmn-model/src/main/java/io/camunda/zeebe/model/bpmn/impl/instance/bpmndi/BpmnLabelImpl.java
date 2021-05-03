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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ATTRIBUTE_LABEL_STYLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ELEMENT_BPMN_LABEL;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;

import io.zeebe.model.bpmn.impl.instance.di.LabelImpl;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnLabel;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnLabelStyle;
import io.zeebe.model.bpmn.instance.di.Label;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMNDI BPMNLabel element
 *
 * @author Sebastian Menski
 */
public class BpmnLabelImpl extends LabelImpl implements BpmnLabel {

  protected static AttributeReference<BpmnLabelStyle> labelStyleAttribute;

  public BpmnLabelImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BpmnLabel.class, BPMNDI_ELEMENT_BPMN_LABEL)
            .namespaceUri(BPMNDI_NS)
            .extendsType(Label.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BpmnLabel>() {
                  @Override
                  public BpmnLabel newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new BpmnLabelImpl(instanceContext);
                  }
                });

    labelStyleAttribute =
        typeBuilder
            .stringAttribute(BPMNDI_ATTRIBUTE_LABEL_STYLE)
            .qNameAttributeReference(BpmnLabelStyle.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public BpmnLabelStyle getLabelStyle() {
    return labelStyleAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setLabelStyle(final BpmnLabelStyle labelStyle) {
    labelStyleAttribute.setReferenceTargetElement(this, labelStyle);
  }
}

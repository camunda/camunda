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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ATTRIBUTE_MESSAGE_VISIBLE_KIND;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ATTRIBUTE_SOURCE_ELEMENT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ATTRIBUTE_TARGET_ELEMENT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ELEMENT_BPMN_EDGE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;

import io.zeebe.model.bpmn.impl.instance.di.LabeledEdgeImpl;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnLabel;
import io.zeebe.model.bpmn.instance.bpmndi.MessageVisibleKind;
import io.zeebe.model.bpmn.instance.di.DiagramElement;
import io.zeebe.model.bpmn.instance.di.LabeledEdge;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMNDI BPMNEdge element
 *
 * @author Sebastian Menski
 */
public class BpmnEdgeImpl extends LabeledEdgeImpl implements BpmnEdge {

  protected static AttributeReference<BaseElement> bpmnElementAttribute;
  protected static AttributeReference<DiagramElement> sourceElementAttribute;
  protected static AttributeReference<DiagramElement> targetElementAttribute;
  protected static Attribute<MessageVisibleKind> messageVisibleKindAttribute;
  protected static ChildElement<BpmnLabel> bpmnLabelChild;

  public BpmnEdgeImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BpmnEdge.class, BPMNDI_ELEMENT_BPMN_EDGE)
            .namespaceUri(BPMNDI_NS)
            .extendsType(LabeledEdge.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BpmnEdge>() {
                  @Override
                  public BpmnEdge newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new BpmnEdgeImpl(instanceContext);
                  }
                });

    bpmnElementAttribute =
        typeBuilder
            .stringAttribute(BPMNDI_ATTRIBUTE_BPMN_ELEMENT)
            .qNameAttributeReference(BaseElement.class)
            .build();

    sourceElementAttribute =
        typeBuilder
            .stringAttribute(BPMNDI_ATTRIBUTE_SOURCE_ELEMENT)
            .qNameAttributeReference(DiagramElement.class)
            .build();

    targetElementAttribute =
        typeBuilder
            .stringAttribute(BPMNDI_ATTRIBUTE_TARGET_ELEMENT)
            .qNameAttributeReference(DiagramElement.class)
            .build();

    messageVisibleKindAttribute =
        typeBuilder
            .enumAttribute(BPMNDI_ATTRIBUTE_MESSAGE_VISIBLE_KIND, MessageVisibleKind.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    bpmnLabelChild = sequenceBuilder.element(BpmnLabel.class).build();

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

  @Override
  public DiagramElement getSourceElement() {
    return sourceElementAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setSourceElement(final DiagramElement sourceElement) {
    sourceElementAttribute.setReferenceTargetElement(this, sourceElement);
  }

  @Override
  public DiagramElement getTargetElement() {
    return targetElementAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setTargetElement(final DiagramElement targetElement) {
    targetElementAttribute.setReferenceTargetElement(this, targetElement);
  }

  @Override
  public MessageVisibleKind getMessageVisibleKind() {
    return messageVisibleKindAttribute.getValue(this);
  }

  @Override
  public void setMessageVisibleKind(final MessageVisibleKind messageVisibleKind) {
    messageVisibleKindAttribute.setValue(this, messageVisibleKind);
  }

  @Override
  public BpmnLabel getBpmnLabel() {
    return bpmnLabelChild.getChild(this);
  }

  @Override
  public void setBpmnLabel(final BpmnLabel bpmnLabel) {
    bpmnLabelChild.setChild(this, bpmnLabel);
  }
}

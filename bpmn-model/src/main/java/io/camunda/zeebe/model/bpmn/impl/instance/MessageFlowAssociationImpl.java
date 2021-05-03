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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_INNER_MESSAGE_FLOW_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_OUTER_MESSAGE_FLOW_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_MESSAGE_FLOW_ASSOCIATION;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.MessageFlow;
import io.zeebe.model.bpmn.instance.MessageFlowAssociation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN messageFlowAssociation element
 *
 * @author Sebastian Menski
 */
public class MessageFlowAssociationImpl extends BaseElementImpl implements MessageFlowAssociation {

  protected static AttributeReference<MessageFlow> innerMessageFlowRefAttribute;
  protected static AttributeReference<MessageFlow> outerMessageFlowRefAttribute;

  public MessageFlowAssociationImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(MessageFlowAssociation.class, BPMN_ELEMENT_MESSAGE_FLOW_ASSOCIATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<MessageFlowAssociation>() {
                  @Override
                  public MessageFlowAssociation newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new MessageFlowAssociationImpl(instanceContext);
                  }
                });

    innerMessageFlowRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_INNER_MESSAGE_FLOW_REF)
            .required()
            .qNameAttributeReference(MessageFlow.class)
            .build();

    outerMessageFlowRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_OUTER_MESSAGE_FLOW_REF)
            .required()
            .qNameAttributeReference(MessageFlow.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public MessageFlow getInnerMessageFlow() {
    return innerMessageFlowRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setInnerMessageFlow(final MessageFlow innerMessageFlow) {
    innerMessageFlowRefAttribute.setReferenceTargetElement(this, innerMessageFlow);
  }

  @Override
  public MessageFlow getOuterMessageFlow() {
    return outerMessageFlowRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOuterMessageFlow(final MessageFlow outerMessageFlow) {
    outerMessageFlowRefAttribute.setReferenceTargetElement(this, outerMessageFlow);
  }
}

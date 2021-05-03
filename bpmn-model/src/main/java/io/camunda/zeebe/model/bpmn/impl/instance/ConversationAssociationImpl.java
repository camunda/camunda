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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_INNER_CONVERSATION_NODE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_OUTER_CONVERSATION_NODE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CONVERSATION_ASSOCIATION;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.ConversationAssociation;
import io.zeebe.model.bpmn.instance.ConversationNode;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN conversationAssociation element
 *
 * @author Sebastian Menski
 */
public class ConversationAssociationImpl extends BaseElementImpl
    implements ConversationAssociation {

  protected static AttributeReference<ConversationNode> innerConversationNodeRefAttribute;
  protected static AttributeReference<ConversationNode> outerConversationNodeRefAttribute;

  public ConversationAssociationImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ConversationAssociation.class, BPMN_ELEMENT_CONVERSATION_ASSOCIATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ConversationAssociation>() {
                  @Override
                  public ConversationAssociation newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ConversationAssociationImpl(instanceContext);
                  }
                });

    innerConversationNodeRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_INNER_CONVERSATION_NODE_REF)
            .required()
            .qNameAttributeReference(ConversationNode.class)
            .build();

    outerConversationNodeRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_OUTER_CONVERSATION_NODE_REF)
            .required()
            .qNameAttributeReference(ConversationNode.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public ConversationNode getInnerConversationNode() {
    return innerConversationNodeRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setInnerConversationNode(final ConversationNode innerConversationNode) {
    innerConversationNodeRefAttribute.setReferenceTargetElement(this, innerConversationNode);
  }

  @Override
  public ConversationNode getOuterConversationNode() {
    return outerConversationNodeRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOuterConversationNode(final ConversationNode outerConversationNode) {
    outerConversationNodeRefAttribute.setReferenceTargetElement(this, outerConversationNode);
  }
}

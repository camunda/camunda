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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CONVERSATION_NODE;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.ConversationNode;
import io.zeebe.model.bpmn.instance.CorrelationKey;
import io.zeebe.model.bpmn.instance.MessageFlow;
import io.zeebe.model.bpmn.instance.Participant;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN conversationNode element
 *
 * @author Sebastian Menski
 */
public abstract class ConversationNodeImpl extends BaseElementImpl implements ConversationNode {

  protected static Attribute<String> nameAttribute;
  protected static ElementReferenceCollection<Participant, ParticipantRef> participantRefCollection;
  protected static ElementReferenceCollection<MessageFlow, MessageFlowRef> messageFlowRefCollection;
  protected static ChildElementCollection<CorrelationKey> correlationKeyCollection;

  public ConversationNodeImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ConversationNode.class, BPMN_ELEMENT_CONVERSATION_NODE)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .abstractType();

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    participantRefCollection =
        sequenceBuilder
            .elementCollection(ParticipantRef.class)
            .qNameElementReferenceCollection(Participant.class)
            .build();

    messageFlowRefCollection =
        sequenceBuilder
            .elementCollection(MessageFlowRef.class)
            .qNameElementReferenceCollection(MessageFlow.class)
            .build();

    correlationKeyCollection = sequenceBuilder.elementCollection(CorrelationKey.class).build();

    typeBuilder.build();
  }

  @Override
  public String getName() {
    return nameAttribute.getValue(this);
  }

  @Override
  public void setName(final String name) {
    nameAttribute.setValue(this, name);
  }

  @Override
  public Collection<Participant> getParticipants() {
    return participantRefCollection.getReferenceTargetElements(this);
  }

  @Override
  public Collection<MessageFlow> getMessageFlows() {
    return messageFlowRefCollection.getReferenceTargetElements(this);
  }

  @Override
  public Collection<CorrelationKey> getCorrelationKeys() {
    return correlationKeyCollection.get(this);
  }
}

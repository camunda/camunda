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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_CLOSED;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_COLLABORATION;

import io.zeebe.model.bpmn.instance.Artifact;
import io.zeebe.model.bpmn.instance.Collaboration;
import io.zeebe.model.bpmn.instance.ConversationAssociation;
import io.zeebe.model.bpmn.instance.ConversationLink;
import io.zeebe.model.bpmn.instance.ConversationNode;
import io.zeebe.model.bpmn.instance.CorrelationKey;
import io.zeebe.model.bpmn.instance.MessageFlow;
import io.zeebe.model.bpmn.instance.MessageFlowAssociation;
import io.zeebe.model.bpmn.instance.Participant;
import io.zeebe.model.bpmn.instance.ParticipantAssociation;
import io.zeebe.model.bpmn.instance.RootElement;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN collaboration element
 *
 * @author Sebastian Menski
 */
public class CollaborationImpl extends RootElementImpl implements Collaboration {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<Boolean> isClosedAttribute;
  protected static ChildElementCollection<Participant> participantCollection;
  protected static ChildElementCollection<MessageFlow> messageFlowCollection;
  protected static ChildElementCollection<Artifact> artifactCollection;
  protected static ChildElementCollection<ConversationNode> conversationNodeCollection;
  protected static ChildElementCollection<ConversationAssociation>
      conversationAssociationCollection;
  protected static ChildElementCollection<ParticipantAssociation> participantAssociationCollection;
  protected static ChildElementCollection<MessageFlowAssociation> messageFlowAssociationCollection;
  protected static ChildElementCollection<CorrelationKey> correlationKeyCollection;
  /** TODO: choreographyRef */
  protected static ChildElementCollection<ConversationLink> conversationLinkCollection;

  public CollaborationImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Collaboration.class, BPMN_ELEMENT_COLLABORATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(RootElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Collaboration>() {
                  @Override
                  public Collaboration newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new CollaborationImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    isClosedAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_CLOSED).defaultValue(false).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    participantCollection = sequenceBuilder.elementCollection(Participant.class).build();

    messageFlowCollection = sequenceBuilder.elementCollection(MessageFlow.class).build();

    artifactCollection = sequenceBuilder.elementCollection(Artifact.class).build();

    conversationNodeCollection = sequenceBuilder.elementCollection(ConversationNode.class).build();

    conversationAssociationCollection =
        sequenceBuilder.elementCollection(ConversationAssociation.class).build();

    participantAssociationCollection =
        sequenceBuilder.elementCollection(ParticipantAssociation.class).build();

    messageFlowAssociationCollection =
        sequenceBuilder.elementCollection(MessageFlowAssociation.class).build();

    correlationKeyCollection = sequenceBuilder.elementCollection(CorrelationKey.class).build();

    conversationLinkCollection = sequenceBuilder.elementCollection(ConversationLink.class).build();

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
  public boolean isClosed() {
    return isClosedAttribute.getValue(this);
  }

  @Override
  public void setClosed(final boolean isClosed) {
    isClosedAttribute.setValue(this, isClosed);
  }

  @Override
  public Collection<Participant> getParticipants() {
    return participantCollection.get(this);
  }

  @Override
  public Collection<MessageFlow> getMessageFlows() {
    return messageFlowCollection.get(this);
  }

  @Override
  public Collection<Artifact> getArtifacts() {
    return artifactCollection.get(this);
  }

  @Override
  public Collection<ConversationNode> getConversationNodes() {
    return conversationNodeCollection.get(this);
  }

  @Override
  public Collection<ConversationAssociation> getConversationAssociations() {
    return conversationAssociationCollection.get(this);
  }

  @Override
  public Collection<ParticipantAssociation> getParticipantAssociations() {
    return participantAssociationCollection.get(this);
  }

  @Override
  public Collection<MessageFlowAssociation> getMessageFlowAssociations() {
    return messageFlowAssociationCollection.get(this);
  }

  @Override
  public Collection<CorrelationKey> getCorrelationKeys() {
    return correlationKeyCollection.get(this);
  }

  @Override
  public Collection<ConversationLink> getConversationLinks() {
    return conversationLinkCollection.get(this);
  }
}

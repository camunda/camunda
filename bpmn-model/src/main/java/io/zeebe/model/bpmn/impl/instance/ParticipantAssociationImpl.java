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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_PARTICIPANT_ASSOCIATION;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.Participant;
import io.zeebe.model.bpmn.instance.ParticipantAssociation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;

/**
 * The BPMN participantAssociation element
 *
 * @author Sebastian Menski
 */
public class ParticipantAssociationImpl extends BaseElementImpl implements ParticipantAssociation {

  protected static ElementReference<Participant, InnerParticipantRef> innerParticipantRefChild;
  protected static ElementReference<Participant, OuterParticipantRef> outerParticipantRefChild;

  public ParticipantAssociationImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ParticipantAssociation.class, BPMN_ELEMENT_PARTICIPANT_ASSOCIATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ParticipantAssociation>() {
                  @Override
                  public ParticipantAssociation newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ParticipantAssociationImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    innerParticipantRefChild =
        sequenceBuilder
            .element(InnerParticipantRef.class)
            .required()
            .qNameElementReference(Participant.class)
            .build();

    outerParticipantRefChild =
        sequenceBuilder
            .element(OuterParticipantRef.class)
            .required()
            .qNameElementReference(Participant.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public Participant getInnerParticipant() {
    return innerParticipantRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setInnerParticipant(final Participant innerParticipant) {
    innerParticipantRefChild.setReferenceTargetElement(this, innerParticipant);
  }

  @Override
  public Participant getOuterParticipant() {
    return outerParticipantRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setOuterParticipant(final Participant outerParticipant) {
    outerParticipantRefChild.setReferenceTargetElement(this, outerParticipant);
  }
}

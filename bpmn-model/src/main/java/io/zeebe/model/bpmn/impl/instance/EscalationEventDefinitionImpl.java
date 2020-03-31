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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ESCALATION_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_ESCALATION_EVENT_DEFINITION;

import io.zeebe.model.bpmn.instance.Escalation;
import io.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.zeebe.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN escalationEventDefinition element
 *
 * @author Sebastian Menski
 */
public class EscalationEventDefinitionImpl extends EventDefinitionImpl
    implements EscalationEventDefinition {

  protected static AttributeReference<Escalation> escalationRefAttribute;

  public EscalationEventDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(EscalationEventDefinition.class, BPMN_ELEMENT_ESCALATION_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<EscalationEventDefinition>() {
                  @Override
                  public EscalationEventDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new EscalationEventDefinitionImpl(instanceContext);
                  }
                });

    escalationRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_ESCALATION_REF)
            .qNameAttributeReference(Escalation.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public Escalation getEscalation() {
    return escalationRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setEscalation(final Escalation escalation) {
    escalationRefAttribute.setReferenceTargetElement(this, escalation);
  }
}

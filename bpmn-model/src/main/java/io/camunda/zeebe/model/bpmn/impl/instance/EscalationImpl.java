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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ESCALATION_CODE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_STRUCTURE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_ESCALATION;

import io.zeebe.model.bpmn.instance.Escalation;
import io.zeebe.model.bpmn.instance.ItemDefinition;
import io.zeebe.model.bpmn.instance.RootElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN escalation element
 *
 * @author Sebastian Menski
 */
public class EscalationImpl extends RootElementImpl implements Escalation {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<String> escalationCodeAttribute;
  protected static AttributeReference<ItemDefinition> structureRefAttribute;

  public EscalationImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Escalation.class, BPMN_ELEMENT_ESCALATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(RootElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Escalation>() {
                  @Override
                  public Escalation newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new EscalationImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    escalationCodeAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_ESCALATION_CODE).build();

    structureRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_STRUCTURE_REF)
            .qNameAttributeReference(ItemDefinition.class)
            .build();

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
  public String getEscalationCode() {
    return escalationCodeAttribute.getValue(this);
  }

  @Override
  public void setEscalationCode(final String escalationCode) {
    escalationCodeAttribute.setValue(this, escalationCode);
  }

  @Override
  public ItemDefinition getStructure() {
    return structureRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setStructure(final ItemDefinition structure) {
    structureRefAttribute.setReferenceTargetElement(this, structure);
  }
}

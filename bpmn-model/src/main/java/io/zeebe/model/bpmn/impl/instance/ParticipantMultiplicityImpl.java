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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_MAXIMUM;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_MINIMUM;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_PARTICIPANT_MULTIPLICITY;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.ParticipantMultiplicity;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN participantMultiplicity element
 *
 * @author Sebastian Menski
 */
public class ParticipantMultiplicityImpl extends BaseElementImpl
    implements ParticipantMultiplicity {

  protected static Attribute<Integer> minimumAttribute;
  protected static Attribute<Integer> maximumAttribute;

  public ParticipantMultiplicityImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ParticipantMultiplicity.class, BPMN_ELEMENT_PARTICIPANT_MULTIPLICITY)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ParticipantMultiplicity>() {
                  @Override
                  public ParticipantMultiplicity newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ParticipantMultiplicityImpl(instanceContext);
                  }
                });

    minimumAttribute = typeBuilder.integerAttribute(BPMN_ATTRIBUTE_MINIMUM).defaultValue(0).build();

    maximumAttribute = typeBuilder.integerAttribute(BPMN_ATTRIBUTE_MAXIMUM).defaultValue(1).build();

    typeBuilder.build();
  }

  @Override
  public int getMinimum() {
    return minimumAttribute.getValue(this);
  }

  @Override
  public void setMinimum(final int minimum) {
    minimumAttribute.setValue(this, minimum);
  }

  @Override
  public int getMaximum() {
    return maximumAttribute.getValue(this);
  }

  @Override
  public void setMaximum(final int maximum) {
    maximumAttribute.setValue(this, maximum);
  }
}

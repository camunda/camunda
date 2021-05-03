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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_LINK_EVENT_DEFINITION;

import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.LinkEventDefinition;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN linkEventDefinition element
 *
 * @author Sebastian Menski
 */
public class LinkEventDefinitionImpl extends EventDefinitionImpl implements LinkEventDefinition {

  protected static Attribute<String> nameAttribute;
  protected static ElementReferenceCollection<LinkEventDefinition, Source> sourceCollection;
  protected static ElementReference<LinkEventDefinition, Target> targetChild;

  public LinkEventDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(LinkEventDefinition.class, BPMN_ELEMENT_LINK_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<LinkEventDefinition>() {
                  @Override
                  public LinkEventDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new LinkEventDefinitionImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).required().build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    sourceCollection =
        sequenceBuilder
            .elementCollection(Source.class)
            .qNameElementReferenceCollection(LinkEventDefinition.class)
            .build();

    targetChild =
        sequenceBuilder
            .element(Target.class)
            .qNameElementReference(LinkEventDefinition.class)
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
  public Collection<LinkEventDefinition> getSources() {
    return sourceCollection.getReferenceTargetElements(this);
  }

  @Override
  public LinkEventDefinition getTarget() {
    return targetChild.getReferenceTargetElement(this);
  }

  @Override
  public void setTarget(final LinkEventDefinition target) {
    targetChild.setReferenceTargetElement(this, target);
  }
}

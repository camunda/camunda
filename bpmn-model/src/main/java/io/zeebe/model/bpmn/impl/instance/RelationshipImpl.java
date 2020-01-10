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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_DIRECTION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_RELATIONSHIP;

import io.zeebe.model.bpmn.RelationshipDirection;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.Relationship;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN relationship element
 *
 * @author Sebastian Menski
 */
public class RelationshipImpl extends BaseElementImpl implements Relationship {

  protected static Attribute<String> typeAttribute;
  protected static Attribute<RelationshipDirection> directionAttribute;
  protected static ChildElementCollection<Source> sourceCollection;
  protected static ChildElementCollection<Target> targetCollection;

  public RelationshipImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Relationship.class, BPMN_ELEMENT_RELATIONSHIP)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Relationship>() {
                  @Override
                  public Relationship newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new RelationshipImpl(instanceContext);
                  }
                });

    typeAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_TYPE).required().build();

    directionAttribute =
        typeBuilder.enumAttribute(BPMN_ATTRIBUTE_DIRECTION, RelationshipDirection.class).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    sourceCollection = sequenceBuilder.elementCollection(Source.class).minOccurs(1).build();

    targetCollection = sequenceBuilder.elementCollection(Target.class).minOccurs(1).build();

    typeBuilder.build();
  }

  @Override
  public String getType() {
    return typeAttribute.getValue(this);
  }

  @Override
  public void setType(final String type) {
    typeAttribute.setValue(this, type);
  }

  @Override
  public RelationshipDirection getDirection() {
    return directionAttribute.getValue(this);
  }

  @Override
  public void setDirection(final RelationshipDirection direction) {
    directionAttribute.setValue(this, direction);
  }

  @Override
  public Collection<Source> getSources() {
    return sourceCollection.get(this);
  }

  @Override
  public Collection<Target> getTargets() {
    return targetCollection.get(this);
  }
}

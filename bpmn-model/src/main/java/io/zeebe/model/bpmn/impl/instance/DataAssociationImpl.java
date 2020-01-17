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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_DATA_ASSOCIATION;

import io.zeebe.model.bpmn.instance.Assignment;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.DataAssociation;
import io.zeebe.model.bpmn.instance.FormalExpression;
import io.zeebe.model.bpmn.instance.ItemAwareElement;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN dataAssociation element
 *
 * @author Sebastian Menski
 */
public class DataAssociationImpl extends BaseElementImpl implements DataAssociation {

  protected static ElementReferenceCollection<ItemAwareElement, SourceRef> sourceRefCollection;
  protected static ElementReference<ItemAwareElement, TargetRef> targetRefChild;
  protected static ChildElement<Transformation> transformationChild;
  protected static ChildElementCollection<Assignment> assignmentCollection;

  public DataAssociationImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(DataAssociation.class, BPMN_ELEMENT_DATA_ASSOCIATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<DataAssociation>() {
                  @Override
                  public DataAssociation newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new DataAssociationImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    sourceRefCollection =
        sequenceBuilder
            .elementCollection(SourceRef.class)
            .idElementReferenceCollection(ItemAwareElement.class)
            .build();

    targetRefChild =
        sequenceBuilder
            .element(TargetRef.class)
            .required()
            .idElementReference(ItemAwareElement.class)
            .build();

    transformationChild = sequenceBuilder.element(Transformation.class).build();

    assignmentCollection = sequenceBuilder.elementCollection(Assignment.class).build();

    typeBuilder.build();
  }

  @Override
  public Collection<ItemAwareElement> getSources() {
    return sourceRefCollection.getReferenceTargetElements(this);
  }

  @Override
  public ItemAwareElement getTarget() {
    return targetRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setTarget(final ItemAwareElement target) {
    targetRefChild.setReferenceTargetElement(this, target);
  }

  @Override
  public FormalExpression getTransformation() {
    return transformationChild.getChild(this);
  }

  @Override
  public void setTransformation(final Transformation transformation) {
    transformationChild.setChild(this, transformation);
  }

  @Override
  public Collection<Assignment> getAssignments() {
    return assignmentCollection.get(this);
  }

  @Override
  public BpmnEdge getDiagramElement() {
    return (BpmnEdge) super.getDiagramElement();
  }
}

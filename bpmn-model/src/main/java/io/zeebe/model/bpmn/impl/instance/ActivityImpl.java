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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_COMPLETION_QUANTITY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_DEFAULT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_FOR_COMPENSATION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_START_QUANTITY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_ACTIVITY;

import io.zeebe.model.bpmn.Query;
import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.impl.QueryImpl;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.DataInputAssociation;
import io.zeebe.model.bpmn.instance.DataOutputAssociation;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.IoSpecification;
import io.zeebe.model.bpmn.instance.LoopCharacteristics;
import io.zeebe.model.bpmn.instance.Property;
import io.zeebe.model.bpmn.instance.ResourceRole;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import java.util.Collection;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN activity element
 *
 * @author Sebastian Menski
 */
public abstract class ActivityImpl extends FlowNodeImpl implements Activity {

  protected static Attribute<Boolean> isForCompensationAttribute;
  protected static Attribute<Integer> startQuantityAttribute;
  protected static Attribute<Integer> completionQuantityAttribute;
  protected static AttributeReference<SequenceFlow> defaultAttribute;
  protected static ChildElement<IoSpecification> ioSpecificationChild;
  protected static ChildElementCollection<Property> propertyCollection;
  protected static ChildElementCollection<DataInputAssociation> dataInputAssociationCollection;
  protected static ChildElementCollection<DataOutputAssociation> dataOutputAssociationCollection;
  protected static ChildElementCollection<ResourceRole> resourceRoleCollection;
  protected static ChildElement<LoopCharacteristics> loopCharacteristicsChild;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Activity.class, BPMN_ELEMENT_ACTIVITY)
            .namespaceUri(BpmnModelConstants.BPMN20_NS)
            .extendsType(FlowNode.class)
            .abstractType();

    isForCompensationAttribute =
        typeBuilder
            .booleanAttribute(BPMN_ATTRIBUTE_IS_FOR_COMPENSATION)
            .defaultValue(false)
            .build();

    startQuantityAttribute =
        typeBuilder.integerAttribute(BPMN_ATTRIBUTE_START_QUANTITY).defaultValue(1).build();

    completionQuantityAttribute =
        typeBuilder.integerAttribute(BPMN_ATTRIBUTE_COMPLETION_QUANTITY).defaultValue(1).build();

    defaultAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_DEFAULT)
            .idAttributeReference(SequenceFlow.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    ioSpecificationChild = sequenceBuilder.element(IoSpecification.class).build();

    propertyCollection = sequenceBuilder.elementCollection(Property.class).build();

    dataInputAssociationCollection =
        sequenceBuilder.elementCollection(DataInputAssociation.class).build();

    dataOutputAssociationCollection =
        sequenceBuilder.elementCollection(DataOutputAssociation.class).build();

    resourceRoleCollection = sequenceBuilder.elementCollection(ResourceRole.class).build();

    loopCharacteristicsChild = sequenceBuilder.element(LoopCharacteristics.class).build();

    typeBuilder.build();
  }

  public ActivityImpl(ModelTypeInstanceContext context) {
    super(context);
  }

  @Override
  public boolean isForCompensation() {
    return isForCompensationAttribute.getValue(this);
  }

  @Override
  public void setForCompensation(boolean isForCompensation) {
    isForCompensationAttribute.setValue(this, isForCompensation);
  }

  @Override
  public int getStartQuantity() {
    return startQuantityAttribute.getValue(this);
  }

  @Override
  public void setStartQuantity(int startQuantity) {
    startQuantityAttribute.setValue(this, startQuantity);
  }

  @Override
  public int getCompletionQuantity() {
    return completionQuantityAttribute.getValue(this);
  }

  @Override
  public void setCompletionQuantity(int completionQuantity) {
    completionQuantityAttribute.setValue(this, completionQuantity);
  }

  @Override
  public SequenceFlow getDefault() {
    return defaultAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setDefault(SequenceFlow defaultFlow) {
    defaultAttribute.setReferenceTargetElement(this, defaultFlow);
  }

  @Override
  public IoSpecification getIoSpecification() {
    return ioSpecificationChild.getChild(this);
  }

  @Override
  public void setIoSpecification(IoSpecification ioSpecification) {
    ioSpecificationChild.setChild(this, ioSpecification);
  }

  @Override
  public Collection<Property> getProperties() {
    return propertyCollection.get(this);
  }

  @Override
  public Collection<DataInputAssociation> getDataInputAssociations() {
    return dataInputAssociationCollection.get(this);
  }

  @Override
  public Collection<DataOutputAssociation> getDataOutputAssociations() {
    return dataOutputAssociationCollection.get(this);
  }

  @Override
  public Collection<ResourceRole> getResourceRoles() {
    return resourceRoleCollection.get(this);
  }

  @Override
  public LoopCharacteristics getLoopCharacteristics() {
    return loopCharacteristicsChild.getChild(this);
  }

  @Override
  public void setLoopCharacteristics(LoopCharacteristics loopCharacteristics) {
    loopCharacteristicsChild.setChild(this, loopCharacteristics);
  }

  @Override
  public Query<BoundaryEvent> getBoundaryEvents() {
    final Collection<BoundaryEvent> queryElements =
        getParentElement().getChildElementsByType(BoundaryEvent.class).stream()
            .filter(event -> event.getAttachedTo().equals(this))
            .collect(Collectors.toSet());

    return new QueryImpl<>(queryElements);
  }
}

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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_PARTITION_ELEMENT_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_LANE;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Lane;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN lane element
 *
 * @author Sebastian Menski
 */
public class LaneImpl extends BaseElementImpl implements Lane {

  protected static Attribute<String> nameAttribute;
  protected static AttributeReference<PartitionElement> partitionElementRefAttribute;
  protected static ChildElement<PartitionElement> partitionElementChild;
  protected static ElementReferenceCollection<FlowNode, FlowNodeRef> flowNodeRefCollection;
  protected static ChildElement<ChildLaneSet> childLaneSetChild;

  public LaneImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Lane.class, BPMN_ELEMENT_LANE)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Lane>() {
                  @Override
                  public Lane newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new LaneImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    partitionElementRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_PARTITION_ELEMENT_REF)
            .qNameAttributeReference(PartitionElement.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    partitionElementChild = sequenceBuilder.element(PartitionElement.class).build();

    flowNodeRefCollection =
        sequenceBuilder
            .elementCollection(FlowNodeRef.class)
            .idElementReferenceCollection(FlowNode.class)
            .build();

    childLaneSetChild = sequenceBuilder.element(ChildLaneSet.class).build();

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
  public PartitionElement getPartitionElement() {
    return partitionElementRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setPartitionElement(final PartitionElement partitionElement) {
    partitionElementRefAttribute.setReferenceTargetElement(this, partitionElement);
  }

  @Override
  public PartitionElement getPartitionElementChild() {
    return partitionElementChild.getChild(this);
  }

  @Override
  public void setPartitionElementChild(final PartitionElement partitionElement) {
    partitionElementChild.setChild(this, partitionElement);
  }

  @Override
  public Collection<FlowNode> getFlowNodeRefs() {
    return flowNodeRefCollection.getReferenceTargetElements(this);
  }

  @Override
  public ChildLaneSet getChildLaneSet() {
    return childLaneSetChild.getChild(this);
  }

  @Override
  public void setChildLaneSet(final ChildLaneSet childLaneSet) {
    childLaneSetChild.setChild(this, childLaneSet);
  }
}

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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_BEHAVIOR;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_IS_SEQUENTIAL;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_MULTI_INSTANCE_LOOP_CHARACTERISTICS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_NONE_BEHAVIOR_EVENT_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_ONE_BEHAVIOR_EVENT_REF;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.MultiInstanceFlowCondition;
import io.zeebe.model.bpmn.builder.MultiInstanceLoopCharacteristicsBuilder;
import io.zeebe.model.bpmn.instance.CompletionCondition;
import io.zeebe.model.bpmn.instance.ComplexBehaviorDefinition;
import io.zeebe.model.bpmn.instance.DataInput;
import io.zeebe.model.bpmn.instance.DataOutput;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.InputDataItem;
import io.zeebe.model.bpmn.instance.LoopCardinality;
import io.zeebe.model.bpmn.instance.LoopCharacteristics;
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.OutputDataItem;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;
import org.camunda.bpm.model.xml.type.reference.ElementReference;

/**
 * The BPMN 2.0 multiInstanceLoopCharacteristics element
 *
 * @author Filip Hrisafov
 */
public class MultiInstanceLoopCharacteristicsImpl extends LoopCharacteristicsImpl
    implements MultiInstanceLoopCharacteristics {

  protected static Attribute<Boolean> isSequentialAttribute;
  protected static Attribute<MultiInstanceFlowCondition> behaviorAttribute;
  protected static AttributeReference<EventDefinition> oneBehaviorEventRefAttribute;
  protected static AttributeReference<EventDefinition> noneBehaviorEventRefAttribute;
  protected static ChildElement<LoopCardinality> loopCardinalityChild;
  protected static ElementReference<DataInput, LoopDataInputRef> loopDataInputRefChild;
  protected static ElementReference<DataOutput, LoopDataOutputRef> loopDataOutputRefChild;
  protected static ChildElement<InputDataItem> inputDataItemChild;
  protected static ChildElement<OutputDataItem> outputDataItemChild;
  protected static ChildElementCollection<ComplexBehaviorDefinition>
      complexBehaviorDefinitionCollection;
  protected static ChildElement<CompletionCondition> completionConditionChild;

  public MultiInstanceLoopCharacteristicsImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(
                MultiInstanceLoopCharacteristics.class,
                BPMN_ELEMENT_MULTI_INSTANCE_LOOP_CHARACTERISTICS)
            .namespaceUri(BPMN20_NS)
            .extendsType(LoopCharacteristics.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<MultiInstanceLoopCharacteristics>() {

                  @Override
                  public MultiInstanceLoopCharacteristics newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new MultiInstanceLoopCharacteristicsImpl(instanceContext);
                  }
                });

    isSequentialAttribute =
        typeBuilder.booleanAttribute(BPMN_ELEMENT_IS_SEQUENTIAL).defaultValue(false).build();

    behaviorAttribute =
        typeBuilder
            .enumAttribute(BPMN_ELEMENT_BEHAVIOR, MultiInstanceFlowCondition.class)
            .defaultValue(MultiInstanceFlowCondition.All)
            .build();

    oneBehaviorEventRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ELEMENT_ONE_BEHAVIOR_EVENT_REF)
            .qNameAttributeReference(EventDefinition.class)
            .build();

    noneBehaviorEventRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ELEMENT_NONE_BEHAVIOR_EVENT_REF)
            .qNameAttributeReference(EventDefinition.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    loopCardinalityChild = sequenceBuilder.element(LoopCardinality.class).build();

    loopDataInputRefChild =
        sequenceBuilder
            .element(LoopDataInputRef.class)
            .qNameElementReference(DataInput.class)
            .build();

    loopDataOutputRefChild =
        sequenceBuilder
            .element(LoopDataOutputRef.class)
            .qNameElementReference(DataOutput.class)
            .build();

    outputDataItemChild = sequenceBuilder.element(OutputDataItem.class).build();

    inputDataItemChild = sequenceBuilder.element(InputDataItem.class).build();

    complexBehaviorDefinitionCollection =
        sequenceBuilder.elementCollection(ComplexBehaviorDefinition.class).build();

    completionConditionChild = sequenceBuilder.element(CompletionCondition.class).build();

    typeBuilder.build();
  }

  @Override
  public MultiInstanceLoopCharacteristicsBuilder builder() {
    return new MultiInstanceLoopCharacteristicsBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public LoopCardinality getLoopCardinality() {
    return loopCardinalityChild.getChild(this);
  }

  @Override
  public void setLoopCardinality(final LoopCardinality loopCardinality) {
    loopCardinalityChild.setChild(this, loopCardinality);
  }

  @Override
  public DataInput getLoopDataInputRef() {
    return loopDataInputRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setLoopDataInputRef(final DataInput loopDataInputRef) {
    loopDataInputRefChild.setReferenceTargetElement(this, loopDataInputRef);
  }

  @Override
  public DataOutput getLoopDataOutputRef() {
    return loopDataOutputRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setLoopDataOutputRef(final DataOutput loopDataOutputRef) {
    loopDataOutputRefChild.setReferenceTargetElement(this, loopDataOutputRef);
  }

  @Override
  public InputDataItem getInputDataItem() {
    return inputDataItemChild.getChild(this);
  }

  @Override
  public void setInputDataItem(final InputDataItem inputDataItem) {
    inputDataItemChild.setChild(this, inputDataItem);
  }

  @Override
  public OutputDataItem getOutputDataItem() {
    return outputDataItemChild.getChild(this);
  }

  @Override
  public void setOutputDataItem(final OutputDataItem outputDataItem) {
    outputDataItemChild.setChild(this, outputDataItem);
  }

  @Override
  public Collection<ComplexBehaviorDefinition> getComplexBehaviorDefinitions() {
    return complexBehaviorDefinitionCollection.get(this);
  }

  @Override
  public CompletionCondition getCompletionCondition() {
    return completionConditionChild.getChild(this);
  }

  @Override
  public void setCompletionCondition(final CompletionCondition completionCondition) {
    completionConditionChild.setChild(this, completionCondition);
  }

  @Override
  public boolean isSequential() {
    return isSequentialAttribute.getValue(this);
  }

  @Override
  public void setSequential(final boolean sequential) {
    isSequentialAttribute.setValue(this, sequential);
  }

  @Override
  public MultiInstanceFlowCondition getBehavior() {
    return behaviorAttribute.getValue(this);
  }

  @Override
  public void setBehavior(final MultiInstanceFlowCondition behavior) {
    behaviorAttribute.setValue(this, behavior);
  }

  @Override
  public EventDefinition getOneBehaviorEventRef() {
    return oneBehaviorEventRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOneBehaviorEventRef(final EventDefinition oneBehaviorEventRef) {
    oneBehaviorEventRefAttribute.setReferenceTargetElement(this, oneBehaviorEventRef);
  }

  @Override
  public EventDefinition getNoneBehaviorEventRef() {
    return noneBehaviorEventRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setNoneBehaviorEventRef(final EventDefinition noneBehaviorEventRef) {
    noneBehaviorEventRefAttribute.setReferenceTargetElement(this, noneBehaviorEventRef);
  }
}

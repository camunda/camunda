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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_OUTPUT_SET;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.DataOutput;
import io.zeebe.model.bpmn.instance.InputSet;
import io.zeebe.model.bpmn.instance.OutputSet;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN outputSet element
 *
 * @author Sebastian Menski
 */
public class OutputSetImpl extends BaseElementImpl implements OutputSet {

  protected static Attribute<String> nameAttribute;
  protected static ElementReferenceCollection<DataOutput, DataOutputRefs> dataOutputRefsCollection;
  protected static ElementReferenceCollection<DataOutput, OptionalOutputRefs>
      optionalOutputRefsCollection;
  protected static ElementReferenceCollection<DataOutput, WhileExecutingOutputRefs>
      whileExecutingOutputRefsCollection;
  protected static ElementReferenceCollection<InputSet, InputSetRefs>
      inputSetInputSetRefsCollection;

  public OutputSetImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(OutputSet.class, BPMN_ELEMENT_OUTPUT_SET)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<OutputSet>() {
                  @Override
                  public OutputSet newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new OutputSetImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    dataOutputRefsCollection =
        sequenceBuilder
            .elementCollection(DataOutputRefs.class)
            .idElementReferenceCollection(DataOutput.class)
            .build();

    optionalOutputRefsCollection =
        sequenceBuilder
            .elementCollection(OptionalOutputRefs.class)
            .idElementReferenceCollection(DataOutput.class)
            .build();

    whileExecutingOutputRefsCollection =
        sequenceBuilder
            .elementCollection(WhileExecutingOutputRefs.class)
            .idElementReferenceCollection(DataOutput.class)
            .build();

    inputSetInputSetRefsCollection =
        sequenceBuilder
            .elementCollection(InputSetRefs.class)
            .idElementReferenceCollection(InputSet.class)
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
  public Collection<DataOutput> getDataOutputRefs() {
    return dataOutputRefsCollection.getReferenceTargetElements(this);
  }

  @Override
  public Collection<DataOutput> getOptionalOutputRefs() {
    return optionalOutputRefsCollection.getReferenceTargetElements(this);
  }

  @Override
  public Collection<DataOutput> getWhileExecutingOutputRefs() {
    return whileExecutingOutputRefsCollection.getReferenceTargetElements(this);
  }

  @Override
  public Collection<InputSet> getInputSetRefs() {
    return inputSetInputSetRefsCollection.getReferenceTargetElements(this);
  }
}

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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_INPUT_DATA_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_OPERATION_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_OUTPUT_DATA_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_IO_BINDING;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.DataInput;
import io.zeebe.model.bpmn.instance.DataOutput;
import io.zeebe.model.bpmn.instance.IoBinding;
import io.zeebe.model.bpmn.instance.Operation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN ioBinding element
 *
 * @author Sebastian Menski
 */
public class IoBindingImpl extends BaseElementImpl implements IoBinding {

  protected static AttributeReference<Operation> operationRefAttribute;
  protected static AttributeReference<DataInput> inputDataRefAttribute;
  protected static AttributeReference<DataOutput> outputDataRefAttribute;

  public IoBindingImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(IoBinding.class, BPMN_ELEMENT_IO_BINDING)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<IoBinding>() {
                  @Override
                  public IoBinding newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new IoBindingImpl(instanceContext);
                  }
                });

    operationRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_OPERATION_REF)
            .required()
            .qNameAttributeReference(Operation.class)
            .build();

    inputDataRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_INPUT_DATA_REF)
            .required()
            .idAttributeReference(DataInput.class)
            .build();

    outputDataRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_OUTPUT_DATA_REF)
            .required()
            .idAttributeReference(DataOutput.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public Operation getOperation() {
    return operationRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOperation(final Operation operation) {
    operationRefAttribute.setReferenceTargetElement(this, operation);
  }

  @Override
  public DataInput getInputData() {
    return inputDataRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setInputData(final DataInput inputData) {
    inputDataRefAttribute.setReferenceTargetElement(this, inputData);
  }

  @Override
  public DataOutput getOutputData() {
    return outputDataRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOutputData(final DataOutput dataOutput) {
    outputDataRefAttribute.setReferenceTargetElement(this, dataOutput);
  }
}

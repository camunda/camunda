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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IMPLEMENTATION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_INSTANTIATE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_OPERATION_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_RECEIVE_TASK;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ReceiveTaskBuilder;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.Operation;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN receiveTask element
 *
 * @author Sebastian Menski
 */
public class ReceiveTaskImpl extends TaskImpl implements ReceiveTask {

  protected static Attribute<String> implementationAttribute;
  protected static Attribute<Boolean> instantiateAttribute;
  protected static AttributeReference<Message> messageRefAttribute;
  protected static AttributeReference<Operation> operationRefAttribute;

  public ReceiveTaskImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ReceiveTask.class, BPMN_ELEMENT_RECEIVE_TASK)
            .namespaceUri(BPMN20_NS)
            .extendsType(Task.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ReceiveTask>() {
                  @Override
                  public ReceiveTask newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new ReceiveTaskImpl(instanceContext);
                  }
                });

    implementationAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_IMPLEMENTATION)
            .defaultValue("##WebService")
            .build();

    instantiateAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_INSTANTIATE).defaultValue(false).build();

    messageRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_MESSAGE_REF)
            .qNameAttributeReference(Message.class)
            .build();

    operationRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_OPERATION_REF)
            .qNameAttributeReference(Operation.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public ReceiveTaskBuilder builder() {
    return new ReceiveTaskBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public String getImplementation() {
    return implementationAttribute.getValue(this);
  }

  @Override
  public void setImplementation(final String implementation) {
    implementationAttribute.setValue(this, implementation);
  }

  @Override
  public boolean instantiate() {
    return instantiateAttribute.getValue(this);
  }

  @Override
  public void setInstantiate(final boolean instantiate) {
    instantiateAttribute.setValue(this, instantiate);
  }

  @Override
  public Message getMessage() {
    return messageRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setMessage(final Message message) {
    messageRefAttribute.setReferenceTargetElement(this, message);
  }

  @Override
  public Operation getOperation() {
    return operationRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOperation(final Operation operation) {
    operationRefAttribute.setReferenceTargetElement(this, operation);
  }
}

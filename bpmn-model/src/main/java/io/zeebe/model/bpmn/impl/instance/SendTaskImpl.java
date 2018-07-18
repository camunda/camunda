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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_OPERATION_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_SEND_TASK;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_CLASS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_DELEGATE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_RESULT_VARIABLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TASK_PRIORITY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TOPIC;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.SendTaskBuilder;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.Operation;
import io.zeebe.model.bpmn.instance.SendTask;
import io.zeebe.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN sendTask element
 *
 * @author Sebastian Menski
 */
public class SendTaskImpl extends TaskImpl implements SendTask {

  protected static Attribute<String> implementationAttribute;
  protected static AttributeReference<Message> messageRefAttribute;
  protected static AttributeReference<Operation> operationRefAttribute;

  /** camunda extensions */
  protected static Attribute<String> camundaClassAttribute;

  protected static Attribute<String> camundaDelegateExpressionAttribute;
  protected static Attribute<String> camundaExpressionAttribute;
  protected static Attribute<String> camundaResultVariableAttribute;
  protected static Attribute<String> camundaTopicAttribute;
  protected static Attribute<String> camundaTypeAttribute;
  protected static Attribute<String> camundaTaskPriorityAttribute;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(SendTask.class, BPMN_ELEMENT_SEND_TASK)
            .namespaceUri(BPMN20_NS)
            .extendsType(Task.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<SendTask>() {
                  @Override
                  public SendTask newInstance(ModelTypeInstanceContext instanceContext) {
                    return new SendTaskImpl(instanceContext);
                  }
                });

    implementationAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_IMPLEMENTATION)
            .defaultValue("##WebService")
            .build();

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

    /** camunda extensions */
    camundaClassAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_CLASS).namespace(CAMUNDA_NS).build();

    camundaDelegateExpressionAttribute =
        typeBuilder
            .stringAttribute(CAMUNDA_ATTRIBUTE_DELEGATE_EXPRESSION)
            .namespace(CAMUNDA_NS)
            .build();

    camundaExpressionAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_EXPRESSION).namespace(CAMUNDA_NS).build();

    camundaResultVariableAttribute =
        typeBuilder
            .stringAttribute(CAMUNDA_ATTRIBUTE_RESULT_VARIABLE)
            .namespace(CAMUNDA_NS)
            .build();

    camundaTopicAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_TOPIC).namespace(CAMUNDA_NS).build();

    camundaTypeAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_TYPE).namespace(CAMUNDA_NS).build();

    camundaTaskPriorityAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_TASK_PRIORITY).namespace(CAMUNDA_NS).build();

    typeBuilder.build();
  }

  public SendTaskImpl(ModelTypeInstanceContext context) {
    super(context);
  }

  @Override
  public SendTaskBuilder builder() {
    return new SendTaskBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public String getImplementation() {
    return implementationAttribute.getValue(this);
  }

  @Override
  public void setImplementation(String implementation) {
    implementationAttribute.setValue(this, implementation);
  }

  @Override
  public Message getMessage() {
    return messageRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setMessage(Message message) {
    messageRefAttribute.setReferenceTargetElement(this, message);
  }

  @Override
  public Operation getOperation() {
    return operationRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setOperation(Operation operation) {
    operationRefAttribute.setReferenceTargetElement(this, operation);
  }

  /** camunda extensions */
  @Override
  public String getCamundaClass() {
    return camundaClassAttribute.getValue(this);
  }

  @Override
  public void setCamundaClass(String camundaClass) {
    camundaClassAttribute.setValue(this, camundaClass);
  }

  @Override
  public String getCamundaDelegateExpression() {
    return camundaDelegateExpressionAttribute.getValue(this);
  }

  @Override
  public void setCamundaDelegateExpression(String camundaExpression) {
    camundaDelegateExpressionAttribute.setValue(this, camundaExpression);
  }

  @Override
  public String getCamundaExpression() {
    return camundaExpressionAttribute.getValue(this);
  }

  @Override
  public void setCamundaExpression(String camundaExpression) {
    camundaExpressionAttribute.setValue(this, camundaExpression);
  }

  @Override
  public String getCamundaResultVariable() {
    return camundaResultVariableAttribute.getValue(this);
  }

  @Override
  public void setCamundaResultVariable(String camundaResultVariable) {
    camundaResultVariableAttribute.setValue(this, camundaResultVariable);
  }

  @Override
  public String getCamundaTopic() {
    return camundaTopicAttribute.getValue(this);
  }

  @Override
  public void setCamundaTopic(String camundaTopic) {
    camundaTopicAttribute.setValue(this, camundaTopic);
  }

  @Override
  public String getCamundaType() {
    return camundaTypeAttribute.getValue(this);
  }

  @Override
  public void setCamundaType(String camundaType) {
    camundaTypeAttribute.setValue(this, camundaType);
  }

  @Override
  public String getCamundaTaskPriority() {
    return camundaTaskPriorityAttribute.getValue(this);
  }

  @Override
  public void setCamundaTaskPriority(String taskPriority) {
    camundaTaskPriorityAttribute.setValue(this, taskPriority);
  }
}

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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_MESSAGE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_CLASS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_DELEGATE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_RESULT_VARIABLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TASK_PRIORITY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TOPIC;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.Operation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;
import org.camunda.bpm.model.xml.type.reference.ElementReference;

/** @author Sebastian Menski */
public class MessageEventDefinitionImpl extends EventDefinitionImpl
    implements MessageEventDefinition {

  protected static AttributeReference<Message> messageRefAttribute;
  protected static ElementReference<Operation, OperationRef> operationRefChild;

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
            .defineType(MessageEventDefinition.class, BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<MessageEventDefinition>() {
                  @Override
                  public MessageEventDefinition newInstance(
                      ModelTypeInstanceContext instanceContext) {
                    return new MessageEventDefinitionImpl(instanceContext);
                  }
                });

    messageRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_MESSAGE_REF)
            .qNameAttributeReference(Message.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    operationRefChild =
        sequenceBuilder.element(OperationRef.class).qNameElementReference(Operation.class).build();

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

  public MessageEventDefinitionImpl(ModelTypeInstanceContext context) {
    super(context);
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
    return operationRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setOperation(Operation operation) {
    operationRefChild.setReferenceTargetElement(this, operation);
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

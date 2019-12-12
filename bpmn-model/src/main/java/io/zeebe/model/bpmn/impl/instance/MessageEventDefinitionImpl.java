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

import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.Operation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;
import org.camunda.bpm.model.xml.type.reference.ElementReference;

/** @author Sebastian Menski */
public class MessageEventDefinitionImpl extends EventDefinitionImpl
    implements MessageEventDefinition {

  protected static AttributeReference<Message> messageRefAttribute;
  protected static ElementReference<Operation, OperationRef> operationRefChild;

  public MessageEventDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(MessageEventDefinition.class, BPMN_ELEMENT_MESSAGE_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<MessageEventDefinition>() {
                  @Override
                  public MessageEventDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
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

    typeBuilder.build();
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
    return operationRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setOperation(final Operation operation) {
    operationRefChild.setReferenceTargetElement(this, operation);
  }
}

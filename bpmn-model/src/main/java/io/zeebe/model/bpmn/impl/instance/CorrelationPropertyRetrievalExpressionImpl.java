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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CORRELATION_PROPERTY_RETRIEVAL_EXPRESSION;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.CorrelationPropertyRetrievalExpression;
import io.zeebe.model.bpmn.instance.Message;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN correlationPropertyRetrievalExpression element
 *
 * @author Sebastian Menski
 */
public class CorrelationPropertyRetrievalExpressionImpl extends BaseElementImpl
    implements CorrelationPropertyRetrievalExpression {

  protected static AttributeReference<Message> messageRefAttribute;
  protected static ChildElement<MessagePath> messagePathChild;

  public CorrelationPropertyRetrievalExpressionImpl(
      final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(
                CorrelationPropertyRetrievalExpression.class,
                BPMN_ELEMENT_CORRELATION_PROPERTY_RETRIEVAL_EXPRESSION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<CorrelationPropertyRetrievalExpression>() {
                  @Override
                  public CorrelationPropertyRetrievalExpression newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new CorrelationPropertyRetrievalExpressionImpl(instanceContext);
                  }
                });

    messageRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_MESSAGE_REF)
            .required()
            .qNameAttributeReference(Message.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    messagePathChild = sequenceBuilder.element(MessagePath.class).required().build();

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
  public MessagePath getMessagePath() {
    return messagePathChild.getChild(this);
  }

  @Override
  public void setMessagePath(final MessagePath messagePath) {
    messagePathChild.setChild(this, messagePath);
  }
}

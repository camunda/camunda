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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_IMPLEMENTATION_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_OPERATION;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.Error;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.Operation;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN operation element
 *
 * @author Sebastian Menski
 */
public class OperationImpl extends BaseElementImpl implements Operation {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<String> implementationRefAttribute;
  protected static ElementReference<Message, InMessageRef> inMessageRefChild;
  protected static ElementReference<Message, OutMessageRef> outMessageRefChild;
  protected static ElementReferenceCollection<Error, ErrorRef> errorRefCollection;

  public OperationImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Operation.class, BPMN_ELEMENT_OPERATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Operation>() {
                  @Override
                  public Operation newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new OperationImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).required().build();

    implementationRefAttribute =
        typeBuilder.stringAttribute(BPMN_ELEMENT_IMPLEMENTATION_REF).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    inMessageRefChild =
        sequenceBuilder
            .element(InMessageRef.class)
            .required()
            .qNameElementReference(Message.class)
            .build();

    outMessageRefChild =
        sequenceBuilder.element(OutMessageRef.class).qNameElementReference(Message.class).build();

    errorRefCollection =
        sequenceBuilder
            .elementCollection(ErrorRef.class)
            .qNameElementReferenceCollection(Error.class)
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
  public String getImplementationRef() {
    return implementationRefAttribute.getValue(this);
  }

  @Override
  public void setImplementationRef(final String implementationRef) {
    implementationRefAttribute.setValue(this, implementationRef);
  }

  @Override
  public Message getInMessage() {
    return inMessageRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setInMessage(final Message message) {
    inMessageRefChild.setReferenceTargetElement(this, message);
  }

  @Override
  public Message getOutMessage() {
    return outMessageRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setOutMessage(final Message message) {
    outMessageRefChild.setReferenceTargetElement(this, message);
  }

  @Override
  public Collection<Error> getErrors() {
    return errorRefCollection.getReferenceTargetElements(this);
  }
}

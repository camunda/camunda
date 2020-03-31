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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CALLABLE_ELEMENT;

import io.zeebe.model.bpmn.instance.CallableElement;
import io.zeebe.model.bpmn.instance.Interface;
import io.zeebe.model.bpmn.instance.IoBinding;
import io.zeebe.model.bpmn.instance.IoSpecification;
import io.zeebe.model.bpmn.instance.RootElement;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN callableElement element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class CallableElementImpl extends RootElementImpl implements CallableElement {

  protected static Attribute<String> nameAttribute;
  protected static ElementReferenceCollection<Interface, SupportedInterfaceRef>
      supportedInterfaceRefCollection;
  protected static ChildElement<IoSpecification> ioSpecificationChild;
  protected static ChildElementCollection<IoBinding> ioBindingCollection;

  public CallableElementImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder bpmnModelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        bpmnModelBuilder
            .defineType(CallableElement.class, BPMN_ELEMENT_CALLABLE_ELEMENT)
            .namespaceUri(BPMN20_NS)
            .extendsType(RootElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ModelElementInstance>() {
                  @Override
                  public ModelElementInstance newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new CallableElementImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    supportedInterfaceRefCollection =
        sequenceBuilder
            .elementCollection(SupportedInterfaceRef.class)
            .qNameElementReferenceCollection(Interface.class)
            .build();

    ioSpecificationChild = sequenceBuilder.element(IoSpecification.class).build();

    ioBindingCollection = sequenceBuilder.elementCollection(IoBinding.class).build();

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
  public Collection<Interface> getSupportedInterfaces() {
    return supportedInterfaceRefCollection.getReferenceTargetElements(this);
  }

  @Override
  public IoSpecification getIoSpecification() {
    return ioSpecificationChild.getChild(this);
  }

  @Override
  public void setIoSpecification(final IoSpecification ioSpecification) {
    ioSpecificationChild.setChild(this, ioSpecification);
  }

  @Override
  public Collection<IoBinding> getIoBindings() {
    return ioBindingCollection.get(this);
  }
}

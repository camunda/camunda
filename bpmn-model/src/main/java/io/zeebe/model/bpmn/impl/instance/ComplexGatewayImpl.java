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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_DEFAULT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_COMPLEX_GATEWAY;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.ComplexGatewayBuilder;
import io.zeebe.model.bpmn.instance.ActivationCondition;
import io.zeebe.model.bpmn.instance.ComplexGateway;
import io.zeebe.model.bpmn.instance.Gateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN complexGateway element
 *
 * @author Sebastian Menski
 */
public class ComplexGatewayImpl extends GatewayImpl implements ComplexGateway {

  protected static AttributeReference<SequenceFlow> defaultAttribute;
  protected static ChildElement<ActivationCondition> activationConditionChild;

  public ComplexGatewayImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ComplexGateway.class, BPMN_ELEMENT_COMPLEX_GATEWAY)
            .namespaceUri(BPMN20_NS)
            .extendsType(Gateway.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ComplexGateway>() {
                  @Override
                  public ComplexGateway newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ComplexGatewayImpl(instanceContext);
                  }
                });

    defaultAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_DEFAULT)
            .idAttributeReference(SequenceFlow.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    activationConditionChild = sequenceBuilder.element(ActivationCondition.class).build();

    typeBuilder.build();
  }

  @Override
  public ComplexGatewayBuilder builder() {
    return new ComplexGatewayBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public SequenceFlow getDefault() {
    return defaultAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setDefault(final SequenceFlow defaultFlow) {
    defaultAttribute.setReferenceTargetElement(this, defaultFlow);
  }

  @Override
  public ActivationCondition getActivationCondition() {
    return activationConditionChild.getChild(this);
  }

  @Override
  public void setActivationCondition(final ActivationCondition activationCondition) {
    activationConditionChild.setChild(this, activationCondition);
  }
}

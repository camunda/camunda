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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_PARAMETER_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_RESOURCE_PARAMETER_BINDING;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.Expression;
import io.zeebe.model.bpmn.instance.ResourceParameter;
import io.zeebe.model.bpmn.instance.ResourceParameterBinding;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN resourceParameterBinding element
 *
 * @author Sebastian Menski
 */
public class ResourceParameterBindingImpl extends BaseElementImpl
    implements ResourceParameterBinding {

  protected static AttributeReference<ResourceParameter> parameterRefAttribute;
  protected static ChildElement<Expression> expressionChild;

  public ResourceParameterBindingImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ResourceParameterBinding.class, BPMN_ELEMENT_RESOURCE_PARAMETER_BINDING)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ResourceParameterBinding>() {
                  @Override
                  public ResourceParameterBinding newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ResourceParameterBindingImpl(instanceContext);
                  }
                });

    parameterRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_PARAMETER_REF)
            .required()
            .qNameAttributeReference(ResourceParameter.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    expressionChild = sequenceBuilder.element(Expression.class).required().build();

    typeBuilder.build();
  }

  @Override
  public ResourceParameter getParameter() {
    return parameterRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setParameter(final ResourceParameter parameter) {
    parameterRefAttribute.setReferenceTargetElement(this, parameter);
  }

  @Override
  public Expression getExpression() {
    return expressionChild.getChild(this);
  }

  @Override
  public void setExpression(final Expression expression) {
    expressionChild.setChild(this, expression);
  }
}

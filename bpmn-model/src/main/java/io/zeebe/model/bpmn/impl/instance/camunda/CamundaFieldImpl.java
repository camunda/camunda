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

package io.zeebe.model.bpmn.impl.instance.camunda;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_STRING_VALUE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_FIELD;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaExpression;
import io.zeebe.model.bpmn.instance.camunda.CamundaField;
import io.zeebe.model.bpmn.instance.camunda.CamundaString;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN field camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaFieldImpl extends BpmnModelElementInstanceImpl implements CamundaField {

  protected static Attribute<String> camundaNameAttribute;
  protected static Attribute<String> camundaExpressionAttribute;
  protected static Attribute<String> camundaStringValueAttribute;
  protected static ChildElement<CamundaExpression> camundaExpressionChild;
  protected static ChildElement<CamundaString> camundaStringChild;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaField.class, CAMUNDA_ELEMENT_FIELD)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaField>() {
                  @Override
                  public CamundaField newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaFieldImpl(instanceContext);
                  }
                });

    camundaNameAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_NAME).namespace(CAMUNDA_NS).build();

    camundaExpressionAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_EXPRESSION).namespace(CAMUNDA_NS).build();

    camundaStringValueAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_STRING_VALUE).namespace(CAMUNDA_NS).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    camundaExpressionChild = sequenceBuilder.element(CamundaExpression.class).build();

    camundaStringChild = sequenceBuilder.element(CamundaString.class).build();

    typeBuilder.build();
  }

  public CamundaFieldImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getCamundaName() {
    return camundaNameAttribute.getValue(this);
  }

  @Override
  public void setCamundaName(String camundaName) {
    camundaNameAttribute.setValue(this, camundaName);
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
  public String getCamundaStringValue() {
    return camundaStringValueAttribute.getValue(this);
  }

  @Override
  public void setCamundaStringValue(String camundaStringValue) {
    camundaStringValueAttribute.setValue(this, camundaStringValue);
  }

  @Override
  public CamundaString getCamundaString() {
    return camundaStringChild.getChild(this);
  }

  @Override
  public void setCamundaString(CamundaString camundaString) {
    camundaStringChild.setChild(this, camundaString);
  }

  @Override
  public CamundaExpression getCamundaExpressionChild() {
    return camundaExpressionChild.getChild(this);
  }

  @Override
  public void setCamundaExpressionChild(CamundaExpression camundaExpression) {
    camundaExpressionChild.setChild(this, camundaExpression);
  }
}

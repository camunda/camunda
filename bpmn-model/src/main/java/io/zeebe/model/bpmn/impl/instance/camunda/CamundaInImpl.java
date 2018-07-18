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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_BUSINESS_KEY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_LOCAL;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_SOURCE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_SOURCE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TARGET;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_VARIABLES;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_IN;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaIn;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN in camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaInImpl extends BpmnModelElementInstanceImpl implements CamundaIn {

  protected static Attribute<String> camundaSourceAttribute;
  protected static Attribute<String> camundaSourceExpressionAttribute;
  protected static Attribute<String> camundaVariablesAttribute;
  protected static Attribute<String> camundaTargetAttribute;
  protected static Attribute<String> camundaBusinessKeyAttribute;
  protected static Attribute<Boolean> camundaLocalAttribute;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaIn.class, CAMUNDA_ELEMENT_IN)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaIn>() {
                  @Override
                  public CamundaIn newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaInImpl(instanceContext);
                  }
                });

    camundaSourceAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_SOURCE).namespace(CAMUNDA_NS).build();

    camundaSourceExpressionAttribute =
        typeBuilder
            .stringAttribute(CAMUNDA_ATTRIBUTE_SOURCE_EXPRESSION)
            .namespace(CAMUNDA_NS)
            .build();

    camundaVariablesAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_VARIABLES).namespace(CAMUNDA_NS).build();

    camundaTargetAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_TARGET).namespace(CAMUNDA_NS).build();

    camundaBusinessKeyAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_BUSINESS_KEY).namespace(CAMUNDA_NS).build();

    camundaLocalAttribute =
        typeBuilder.booleanAttribute(CAMUNDA_ATTRIBUTE_LOCAL).namespace(CAMUNDA_NS).build();

    typeBuilder.build();
  }

  public CamundaInImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getCamundaSource() {
    return camundaSourceAttribute.getValue(this);
  }

  @Override
  public void setCamundaSource(String camundaSource) {
    camundaSourceAttribute.setValue(this, camundaSource);
  }

  @Override
  public String getCamundaSourceExpression() {
    return camundaSourceExpressionAttribute.getValue(this);
  }

  @Override
  public void setCamundaSourceExpression(String camundaSourceExpression) {
    camundaSourceExpressionAttribute.setValue(this, camundaSourceExpression);
  }

  @Override
  public String getCamundaVariables() {
    return camundaVariablesAttribute.getValue(this);
  }

  @Override
  public void setCamundaVariables(String camundaVariables) {
    camundaVariablesAttribute.setValue(this, camundaVariables);
  }

  @Override
  public String getCamundaTarget() {
    return camundaTargetAttribute.getValue(this);
  }

  @Override
  public void setCamundaTarget(String camundaTarget) {
    camundaTargetAttribute.setValue(this, camundaTarget);
  }

  @Override
  public String getCamundaBusinessKey() {
    return camundaBusinessKeyAttribute.getValue(this);
  }

  @Override
  public void setCamundaBusinessKey(String camundaBusinessKey) {
    camundaBusinessKeyAttribute.setValue(this, camundaBusinessKey);
  }

  @Override
  public boolean getCamundaLocal() {
    return camundaLocalAttribute.getValue(this);
  }

  @Override
  public void setCamundaLocal(boolean camundaLocal) {
    camundaLocalAttribute.setValue(this, camundaLocal);
  }
}

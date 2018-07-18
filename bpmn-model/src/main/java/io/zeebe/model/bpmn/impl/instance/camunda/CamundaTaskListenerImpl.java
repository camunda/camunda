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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_CLASS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_DELEGATE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_EVENT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_EXPRESSION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_TASK_LISTENER;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaField;
import io.zeebe.model.bpmn.instance.camunda.CamundaScript;
import io.zeebe.model.bpmn.instance.camunda.CamundaTaskListener;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN taskListener camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaTaskListenerImpl extends BpmnModelElementInstanceImpl
    implements CamundaTaskListener {

  protected static Attribute<String> camundaEventAttribute;
  protected static Attribute<String> camundaClassAttribute;
  protected static Attribute<String> camundaExpressionAttribute;
  protected static Attribute<String> camundaDelegateExpressionAttribute;
  protected static ChildElementCollection<CamundaField> camundaFieldCollection;
  protected static ChildElement<CamundaScript> camundaScriptChild;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaTaskListener.class, CAMUNDA_ELEMENT_TASK_LISTENER)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaTaskListener>() {
                  @Override
                  public CamundaTaskListener newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaTaskListenerImpl(instanceContext);
                  }
                });

    camundaEventAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_EVENT).namespace(CAMUNDA_NS).build();

    camundaClassAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_CLASS).namespace(CAMUNDA_NS).build();

    camundaExpressionAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_EXPRESSION).namespace(CAMUNDA_NS).build();

    camundaDelegateExpressionAttribute =
        typeBuilder
            .stringAttribute(CAMUNDA_ATTRIBUTE_DELEGATE_EXPRESSION)
            .namespace(CAMUNDA_NS)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    camundaFieldCollection = sequenceBuilder.elementCollection(CamundaField.class).build();

    camundaScriptChild = sequenceBuilder.element(CamundaScript.class).build();

    typeBuilder.build();
  }

  public CamundaTaskListenerImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getCamundaEvent() {
    return camundaEventAttribute.getValue(this);
  }

  @Override
  public void setCamundaEvent(String camundaEvent) {
    camundaEventAttribute.setValue(this, camundaEvent);
  }

  @Override
  public String getCamundaClass() {
    return camundaClassAttribute.getValue(this);
  }

  @Override
  public void setCamundaClass(String camundaClass) {
    camundaClassAttribute.setValue(this, camundaClass);
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
  public String getCamundaDelegateExpression() {
    return camundaDelegateExpressionAttribute.getValue(this);
  }

  @Override
  public void setCamundaDelegateExpression(String camundaDelegateExpression) {
    camundaDelegateExpressionAttribute.setValue(this, camundaDelegateExpression);
  }

  @Override
  public Collection<CamundaField> getCamundaFields() {
    return camundaFieldCollection.get(this);
  }

  @Override
  public CamundaScript getCamundaScript() {
    return camundaScriptChild.getChild(this);
  }

  @Override
  public void setCamundaScript(CamundaScript camundaScript) {
    camundaScriptChild.setChild(this, camundaScript);
  }
}

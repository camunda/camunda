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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_DATE_PATTERN;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_DEFAULT_VALUE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_LABEL;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_FORM_FIELD;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormField;
import io.zeebe.model.bpmn.instance.camunda.CamundaProperties;
import io.zeebe.model.bpmn.instance.camunda.CamundaValidation;
import io.zeebe.model.bpmn.instance.camunda.CamundaValue;
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
 * The BPMN formField camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaFormFieldImpl extends BpmnModelElementInstanceImpl implements CamundaFormField {

  protected static Attribute<String> camundaIdAttribute;
  protected static Attribute<String> camundaLabelAttribute;
  protected static Attribute<String> camundaTypeAttribute;
  protected static Attribute<String> camundaDatePatternAttribute;
  protected static Attribute<String> camundaDefaultValueAttribute;
  protected static ChildElement<CamundaProperties> camundaPropertiesChild;
  protected static ChildElement<CamundaValidation> camundaValidationChild;
  protected static ChildElementCollection<CamundaValue> camundaValueCollection;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaFormField.class, CAMUNDA_ELEMENT_FORM_FIELD)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaFormField>() {
                  @Override
                  public CamundaFormField newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaFormFieldImpl(instanceContext);
                  }
                });

    camundaIdAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_ID).namespace(CAMUNDA_NS).build();

    camundaLabelAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_LABEL).namespace(CAMUNDA_NS).build();

    camundaTypeAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_TYPE).namespace(CAMUNDA_NS).build();

    camundaDatePatternAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_DATE_PATTERN).namespace(CAMUNDA_NS).build();

    camundaDefaultValueAttribute =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_DEFAULT_VALUE).namespace(CAMUNDA_NS).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    camundaPropertiesChild = sequenceBuilder.element(CamundaProperties.class).build();

    camundaValidationChild = sequenceBuilder.element(CamundaValidation.class).build();

    camundaValueCollection = sequenceBuilder.elementCollection(CamundaValue.class).build();

    typeBuilder.build();
  }

  public CamundaFormFieldImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getCamundaId() {
    return camundaIdAttribute.getValue(this);
  }

  @Override
  public void setCamundaId(String camundaId) {
    camundaIdAttribute.setValue(this, camundaId);
  }

  @Override
  public String getCamundaLabel() {
    return camundaLabelAttribute.getValue(this);
  }

  @Override
  public void setCamundaLabel(String camundaLabel) {
    camundaLabelAttribute.setValue(this, camundaLabel);
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
  public String getCamundaDatePattern() {
    return camundaDatePatternAttribute.getValue(this);
  }

  @Override
  public void setCamundaDatePattern(String camundaDatePattern) {
    camundaDatePatternAttribute.setValue(this, camundaDatePattern);
  }

  @Override
  public String getCamundaDefaultValue() {
    return camundaDefaultValueAttribute.getValue(this);
  }

  @Override
  public void setCamundaDefaultValue(String camundaDefaultValue) {
    camundaDefaultValueAttribute.setValue(this, camundaDefaultValue);
  }

  @Override
  public CamundaProperties getCamundaProperties() {
    return camundaPropertiesChild.getChild(this);
  }

  @Override
  public void setCamundaProperties(CamundaProperties camundaProperties) {
    camundaPropertiesChild.setChild(this, camundaProperties);
  }

  @Override
  public CamundaValidation getCamundaValidation() {
    return camundaValidationChild.getChild(this);
  }

  @Override
  public void setCamundaValidation(CamundaValidation camundaValidation) {
    camundaValidationChild.setChild(this, camundaValidation);
  }

  @Override
  public Collection<CamundaValue> getCamundaValues() {
    return camundaValueCollection.get(this);
  }
}

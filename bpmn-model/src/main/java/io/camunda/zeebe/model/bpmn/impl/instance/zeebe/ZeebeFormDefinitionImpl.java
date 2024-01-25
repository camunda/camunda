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
package io.camunda.zeebe.model.bpmn.impl.instance.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeFormDefinitionImpl extends BpmnModelElementInstanceImpl
    implements ZeebeFormDefinition {

  protected static Attribute<String> formKeyAttribute;
  protected static Attribute<String> formIdAttribute;
  protected static Attribute<String> externalReferenceAttribute;

  public ZeebeFormDefinitionImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getFormKey() {
    return formKeyAttribute.getValue(this);
  }

  @Override
  public void setFormKey(final String formKey) {
    formKeyAttribute.setValue(this, formKey);
  }

  @Override
  public String getFormId() {
    return formIdAttribute.getValue(this);
  }

  @Override
  public void setFormId(final String formId) {
    formIdAttribute.setValue(this, formId);
  }

  @Override
  public String getExternalReference() {
    return externalReferenceAttribute.getValue(this);
  }

  @Override
  public void setExternalReference(final String externalReference) {
    externalReferenceAttribute.setValue(this, externalReference);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeFormDefinition.class, ZeebeConstants.ELEMENT_FORM_DEFINITION)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeFormDefinitionImpl::new);

    formKeyAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_FORM_KEY)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    formIdAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_FORM_ID)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    externalReferenceAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_EXTERNAL_REFERENCE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}

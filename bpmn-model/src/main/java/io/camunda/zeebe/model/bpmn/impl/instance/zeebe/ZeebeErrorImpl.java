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

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.ZEEBE_NS;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.ATTRIBUTE_ERROR_CODE_VARIABLE;
import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.ATTRIBUTE_ERROR_MESSAGE_VARIABLE;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeError;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeErrorImpl extends BpmnModelElementInstanceImpl implements ZeebeError {

  protected static Attribute<String> errorCodeVariableAttribute;

  protected static Attribute<String> errorMessageVariableAttribute;

  public ZeebeErrorImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getErrorCodeVariable() {
    return errorCodeVariableAttribute.getValue(this);
  }

  @Override
  public void setErrorCodeVariable(final String errorCodeVariable) {
    errorCodeVariableAttribute.setValue(this, errorCodeVariable);
  }

  @Override
  public String getErrorMessageVariable() {
    return errorMessageVariableAttribute.getValue(this);
  }

  @Override
  public void setErrorMessageVariable(final String errorMessageVariable) {
    errorMessageVariableAttribute.setValue(this, errorMessageVariable);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeError.class, ZeebeConstants.ELEMENT_ERROR)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeErrorImpl::new);

    errorCodeVariableAttribute =
        typeBuilder.stringAttribute(ATTRIBUTE_ERROR_CODE_VARIABLE).namespace(ZEEBE_NS).build();

    errorMessageVariableAttribute =
        typeBuilder.stringAttribute(ATTRIBUTE_ERROR_MESSAGE_VARIABLE).namespace(ZEEBE_NS).build();

    typeBuilder.build();
  }
}

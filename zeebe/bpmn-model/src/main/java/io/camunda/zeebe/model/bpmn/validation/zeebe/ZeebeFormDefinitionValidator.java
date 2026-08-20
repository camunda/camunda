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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeFormDefinitionValidator implements ModelElementValidator<ZeebeFormDefinition> {

  private static final String ERROR_MESSAGE_ONE_NONEMPTY_ELEMENT =
      "Exactly one of the attributes '%s, %s' must be present and not blank%s";

  @Override
  public Class<ZeebeFormDefinition> getElementType() {
    return ZeebeFormDefinition.class;
  }

  @Override
  public void validate(
      final ZeebeFormDefinition element,
      final ValidationResultCollector validationResultCollector) {
    final String formKey = element.getFormKey();
    final String formId = element.getFormId();
    final String externalReference = element.getExternalReference();

    final ModelElementInstance nativeUserTaskElement =
        element
            .getParentElement()
            .getUniqueChildElementByNameNs(
                BpmnModelConstants.ZEEBE_NS, ZeebeConstants.ELEMENT_USER_TASK);

    if (nativeUserTaskElement == null) {
      if (isBlank(formKey) == isBlank(formId)) {
        validationResultCollector.addError(
            0,
            String.format(
                ERROR_MESSAGE_ONE_NONEMPTY_ELEMENT,
                ZeebeConstants.ATTRIBUTE_FORM_ID,
                ZeebeConstants.ATTRIBUTE_FORM_KEY,
                ""));
      }
    } else {
      if (isBlank(externalReference) == isBlank(formId)) {
        validationResultCollector.addError(
            0,
            String.format(
                ERROR_MESSAGE_ONE_NONEMPTY_ELEMENT,
                ZeebeConstants.ATTRIBUTE_FORM_ID,
                ZeebeConstants.ATTRIBUTE_EXTERNAL_REFERENCE,
                " for native user tasks"));
      }
    }
  }

  private boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }
}

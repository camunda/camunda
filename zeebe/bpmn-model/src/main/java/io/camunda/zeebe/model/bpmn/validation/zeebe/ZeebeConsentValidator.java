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

import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConsent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeRelationship;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

/**
 * The connection and the operation of a consent are fixed values, never expressions, so instance
 * data cannot point a task at another connection.
 */
public class ZeebeConsentValidator implements ModelElementValidator<ZeebeConsent> {

  @Override
  public Class<ZeebeConsent> getElementType() {
    return ZeebeConsent.class;
  }

  @Override
  public void validate(
      final ZeebeConsent element, final ValidationResultCollector validationResultCollector) {
    verifyFixedValue("connection", element.getConnection(), validationResultCollector);
    verifyFixedValue("operation", element.getOperation(), validationResultCollector);
    final ModelElementInstance parent = element.getParentElement();
    if (parent instanceof ExtensionElements
        && !((ExtensionElements) parent)
            .getChildElementsByType(ZeebeRelationship.class)
            .isEmpty()) {
      validationResultCollector.addError(
          0,
          "A task rests on one ground: it may declare zeebe:consent or zeebe:relationship, not both.");
    }
  }

  static void verifyFixedValue(
      final String attribute,
      final String value,
      final ValidationResultCollector validationResultCollector) {
    if (value == null || value.trim().isEmpty()) {
      validationResultCollector.addError(
          0, String.format("Attribute '%s' must be present and not empty", attribute));
    } else if (value.startsWith("=")) {
      validationResultCollector.addError(
          0,
          String.format(
              "Expected '%s' to be a fixed value, but found the expression '%s'. Instance data must not choose what a task acts on.",
              attribute, value));
    }
  }
}

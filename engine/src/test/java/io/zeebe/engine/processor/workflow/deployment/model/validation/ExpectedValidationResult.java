/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultType;

public class ExpectedValidationResult {
  private String expectedElementId;
  private Class<? extends BpmnModelElementInstance> expectedElementType;
  private String expectedMessage;
  private ValidationResultType expectedType;

  public static ExpectedValidationResult expect(String elementId, String message) {
    final ExpectedValidationResult result = new ExpectedValidationResult();
    result.expectedElementId = elementId;
    result.expectedMessage = message;
    result.expectedType = ValidationResultType.ERROR;

    return result;
  }

  public static ExpectedValidationResult expect(
      Class<? extends BpmnModelElementInstance> elementType, String message) {
    final ExpectedValidationResult result = new ExpectedValidationResult();
    result.expectedElementType = elementType;
    result.expectedMessage = message;
    result.expectedType = ValidationResultType.ERROR;

    return result;
  }

  public boolean matches(ValidationResult result) {
    boolean match = true;
    final ModelElementInstance element = result.getElement();

    if (this.expectedElementId != null) {
      if (element instanceof BaseElement) {
        if (!((BaseElement) element).getId().equals(expectedElementId)) {
          match = false;
        }
      } else {
        match = false;
      }
    }

    if (this.expectedElementType != null
        && !expectedElementType.isAssignableFrom(element.getClass())) {
      match = false;
    }

    if (!expectedMessage.equals(result.getMessage())) {
      match = false;
    }

    if (expectedType != result.getType()) {
      match = false;
    }

    return match;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    sb.append(expectedType);
    sb.append(": {");
    if (expectedElementId != null) {
      sb.append("id: ");
      sb.append(expectedElementId);
      sb.append(", ");
    }
    if (expectedElementType != null) {
      sb.append("type: ");
      sb.append(expectedElementType.getSimpleName());
      sb.append(", ");
    }
    sb.append("message: ");
    sb.append(expectedMessage);
    sb.append("}");

    return sb.toString();
  }

  public static String toString(ValidationResult result) {
    final ModelElementInstance element = result.getElement();

    final StringBuilder sb = new StringBuilder();
    sb.append(result.getType());
    sb.append(": {");
    if (element instanceof BaseElement) {
      sb.append("id: ");
      sb.append(((BaseElement) element).getId());
      sb.append(", ");
    }
    sb.append("type: ");
    sb.append(element.getElementType().getInstanceType().getSimpleName());
    sb.append(", ");

    sb.append("message: ");
    sb.append(result.getMessage());
    sb.append("}");

    return sb.toString();
  }
}

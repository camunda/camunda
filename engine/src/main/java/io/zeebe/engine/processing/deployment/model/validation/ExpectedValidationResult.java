/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultType;

public final class ExpectedValidationResult {
  private String expectedElementId;
  private Class<? extends BpmnModelElementInstance> expectedElementType;
  private String expectedMessage;
  private ValidationResultType expectedType;

  public static ExpectedValidationResult expect(final String elementId, final String message) {
    final ExpectedValidationResult result = new ExpectedValidationResult();
    result.expectedElementId = elementId;
    result.expectedMessage = message;
    result.expectedType = ValidationResultType.ERROR;

    return result;
  }

  public static ExpectedValidationResult expect(
      final Class<? extends BpmnModelElementInstance> elementType, final String message) {
    final ExpectedValidationResult result = new ExpectedValidationResult();
    result.expectedElementType = elementType;
    result.expectedMessage = message;
    result.expectedType = ValidationResultType.ERROR;

    return result;
  }

  public boolean matches(final ValidationResult result) {
    boolean match = true;
    final ModelElementInstance element = result.getElement();

    if (expectedElementId != null) {
      if (element instanceof BaseElement) {
        if (!((BaseElement) element).getId().equals(expectedElementId)) {
          match = false;
        }
      } else {
        match = false;
      }
    }

    if (expectedElementType != null && !expectedElementType.isAssignableFrom(element.getClass())) {
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

  public static String toString(final ValidationResult result) {
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

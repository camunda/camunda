/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import org.camunda.bpm.model.xml.ModelParseException;

/**
 * Transforms confusing BPMN XML element ordering error messages into clearer, more actionable
 * messages.
 *
 * <p>When BPMN elements appear in the wrong order according to the BPMN 2.0 XML Schema, the XML
 * parser produces confusing error messages. This transformer detects such ordering errors and
 * provides clearer messages explaining the required order.
 */
public final class BpmnElementOrderErrorTransformer {

  /**
   * Transforms a ModelParseException into an improved error message for element ordering
   * violations.
   *
   * <p>When BPMN elements appear in the wrong order (e.g., {@code <outgoing>} after {@code
   * <timerEventDefinition>}), the XML parser produces a confusing error like "Invalid content was
   * found starting with element 'outgoing'". This method detects such ordering errors and provides
   * a clearer message explaining the required order.
   *
   * @param exception the ModelParseException from BPMN parsing
   * @return an improved error message if this is an element ordering error, otherwise the original
   *     message
   */
  public String transform(final ModelParseException exception) {
    final String originalMessage =
        exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
    return improveElementOrderingErrorMessage(originalMessage);
  }

  /**
   * Improves XML Schema validation error messages for element ordering violations.
   *
   * <p>When BPMN elements appear in the wrong order (e.g., {@code <outgoing>} after {@code
   * <timerEventDefinition>}), the XML parser produces a confusing error like "Invalid content was
   * found starting with element 'outgoing'". This method detects such ordering errors and provides
   * a clearer message explaining the required order.
   *
   * @param originalMessage the original error message from the XML parser
   * @return an improved error message if this is an element ordering error, otherwise the original
   *     message
   */
  private String improveElementOrderingErrorMessage(final String originalMessage) {
    if (originalMessage == null) {
      return "Unable to parse BPMN XML";
    }

    if (!isElementOrderingError(originalMessage)) {
      return originalMessage;
    }

    final String problematicElement = extractProblematicElement(originalMessage);
    if (problematicElement == null) {
      return originalMessage;
    }

    final String elementName = extractElementName(problematicElement);
    return formatImprovedErrorMessage(elementName, originalMessage);
  }

  private boolean isElementOrderingError(final String message) {
    return message.contains("cvc-complex-type.2.4")
        && message.contains("Invalid content was found starting with element");
  }

  private String extractProblematicElement(final String message) {
    final String startDelimiter = "starting with element '";
    final String endDelimiter = "'";
    final int startIdx = message.indexOf(startDelimiter);

    if (startIdx == -1) {
      return null;
    }

    final int elementNameStart = startIdx + startDelimiter.length();
    final int elementNameEnd = message.indexOf(endDelimiter, elementNameStart);

    if (elementNameEnd == -1) {
      return null;
    }

    return message.substring(elementNameStart, elementNameEnd);
  }

  /**
   * Extracts the element name from different formats:
   *
   * <ul>
   *   <li>bpmn:outgoing
   *   <li>outgoing
   * </ul>
   */
  private String extractElementName(final String problematicElement) {
    if (problematicElement.contains("}")) {
      return extractNamespaceQualifiedName(problematicElement);
    }

    if (problematicElement.contains(":")) {
      return problematicElement.substring(problematicElement.lastIndexOf(':') + 1);
    }

    return problematicElement;
  }

  private String extractNamespaceQualifiedName(final String element) {
    final int lastColon = element.lastIndexOf(':');
    final int closingBrace = element.indexOf('}');

    if (lastColon != -1 && closingBrace != -1 && lastColon < closingBrace) {
      return element.substring(lastColon + 1, closingBrace);
    }

    return element;
  }

  private String formatImprovedErrorMessage(
      final String elementName, final String originalMessage) {
    if ("outgoing".equals(elementName) || "incoming".equals(elementName)) {
      return String.format(
          "Element '%s' must be placed before event definitions (e.g., timerEventDefinition, "
              + "messageEventDefinition) in the BPMN XML. "
              + "The BPMN 2.0 schema requires flow element references to appear before event definitions. "
              + "Original error: %s",
          elementName, originalMessage);
    }

    return String.format(
        "Element '%s' appears in the wrong position in the BPMN XML. "
            + "The BPMN 2.0 schema requires elements to appear in a specific order. "
            + "Please check the element ordering in your BPMN file. "
            + "Original error: %s",
        elementName, originalMessage);
  }
}

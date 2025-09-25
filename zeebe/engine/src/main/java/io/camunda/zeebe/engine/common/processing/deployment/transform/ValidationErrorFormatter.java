/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.transform;

import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ValidationResult;
import org.camunda.bpm.model.xml.validation.ValidationResultFormatter;

public final class ValidationErrorFormatter implements ValidationResultFormatter {
  public static final String OMITTED_RESULTS_SUFFIX_FORMAT = "and %d more errors and/or warnings";

  @Override
  public void formatElement(final StringWriter writer, final ModelElementInstance element) {
    writer.append("- Element: ");
    writer.append(createElementIdentifier(element));
    writer.append("\n");
  }

  @Override
  public void formatResult(final StringWriter writer, final ValidationResult result) {
    writer.append("    - ");
    writer.append(result.getType().toString());
    writer.append(": ");
    writer.append(result.getMessage());
    writer.append("\n");
  }

  @Override
  public void formatSuffixWithOmittedResultsCount(final StringWriter writer, final int count) {
    writer.append(String.format(OMITTED_RESULTS_SUFFIX_FORMAT, count));
  }

  @Override
  public int getFormattedSuffixWithOmittedResultsSize(final int count) {
    return String.format(OMITTED_RESULTS_SUFFIX_FORMAT, count).getBytes().length;
  }

  /**
   * Build an identifier starting with the closest parent element that has an id.
   *
   * <p>E.g. a service task has a task definition with a validation error, then the identifier
   * should be: <code>taskId > extensionElements > taskDefinition</code>
   */
  private String createElementIdentifier(final ModelElementInstance element) {
    final List<ModelElementInstance> identifiableElementChain = new ArrayList<>();

    ModelElementInstance current = element;
    do {
      identifiableElementChain.add(0, current);
      if (current instanceof BaseElement && ((BaseElement) current).getId() != null) {
        current = null;
      } else {
        current = current.getParentElement();
      }
    } while (current != null);

    final StringBuilder sb = new StringBuilder();

    for (int i = 0; i < identifiableElementChain.size(); i++) {
      final ModelElementInstance chainElement = identifiableElementChain.get(i);
      if (chainElement instanceof BaseElement && ((BaseElement) chainElement).getId() != null) {
        sb.append(((BaseElement) chainElement).getId());
      } else {
        sb.append(chainElement.getElementType().getTypeName());
      }

      if (i < identifiableElementChain.size() - 1) {
        sb.append(" > ");
      }
    }

    return sb.toString();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.SecretReference;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.jspecify.annotations.NullMarked;

/**
 * Rejects a deployment when a {@code camunda.secrets.<name>} reference sits inside a FEEL list
 * literal, or inside a context literal produced by a branch of the expression rather than being the
 * expression's own root (see {@link SecretReference}'s class javadoc, "Known gaps"). Both shapes
 * make {@link SecretReference#parse} report the reference at an enclosing path instead of its own
 * leaf, which can never resolve to a text value at injection - every instance of the element whose
 * evaluated expression produces that container would hit the same {@code SECRET_RESOLUTION_ERROR}
 * incident until the mapping is fixed: unconditionally for a list literal, or only the instances
 * that take the producing branch for a conditional context. Rejecting at deploy time catches this
 * before any instance runs.
 *
 * <p>This does not attempt to cover every way a FEEL expression could produce a container value
 * (e.g. a function call) - only the two shapes named above; see {@link
 * SecretReference#hasImpreciseReference}. Anything else stays undetected, same as {@link
 * SecretReference}'s own documented limitations; {@code JobSecretInjector}'s runtime guard is the
 * safety net for those.
 */
@NullMarked
final class SecretReferenceLeafPrecisionValidator implements ModelElementValidator<ZeebeInput> {

  private final ExpressionLanguage expressionLanguage;

  SecretReferenceLeafPrecisionValidator(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }

  @Override
  public Class<ZeebeInput> getElementType() {
    return ZeebeInput.class;
  }

  @Override
  public void validate(
      final ZeebeInput element, final ValidationResultCollector validationResultCollector) {
    final String source = element.getSource();
    if (source == null) {
      return;
    }

    final Expression expression = expressionLanguage.parseExpression(source);
    if (!expression.isValid()) {
      // invalid expressions are reported separately by the expression validator
      return;
    }

    if (SecretReference.hasImpreciseReference(expression)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Input mapping source '%s' puts a secret reference inside a list, or inside a "
                  + "context built by an 'if' branch. Camunda can only replace a secret where "
                  + "the mapping assigns it directly to a value, so this secret would never be "
                  + "filled in. Assign each secret reference to its own input mapping instead.",
              source));
    }
  }
}

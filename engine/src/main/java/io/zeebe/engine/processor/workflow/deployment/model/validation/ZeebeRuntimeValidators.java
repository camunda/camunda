/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import java.util.ArrayList;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public class ZeebeRuntimeValidators {

  public static final Collection<ModelElementValidator<?>> VALIDATORS;

  static {
    final ZeebeExpressionValidator expressionValidator = new ZeebeExpressionValidator();

    VALIDATORS = new ArrayList<>();
    VALIDATORS.add(new ZeebeInputValidator(expressionValidator));
    VALIDATORS.add(new ZeebeOutputValidator(expressionValidator));
    VALIDATORS.add(new SequenceFlowValidator(expressionValidator));
    VALIDATORS.add(new ZeebeSubscriptionValidator(expressionValidator));
  }
}

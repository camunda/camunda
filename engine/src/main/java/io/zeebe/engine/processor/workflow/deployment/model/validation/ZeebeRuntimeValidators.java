/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public class ZeebeRuntimeValidators {

  private static final ZeebeExpressionValidator EXPRESSION_VALIDATOR =
      new ZeebeExpressionValidator();
  public static final Collection<ModelElementValidator<?>> VALIDATORS =
      List.of(
          new ZeebeInputValidator(EXPRESSION_VALIDATOR),
          new ZeebeOutputValidator(EXPRESSION_VALIDATOR),
          new SequenceFlowValidator(EXPRESSION_VALIDATOR),
          new ZeebeSubscriptionValidator(EXPRESSION_VALIDATOR),
          new ZeebeLoopCharacteristicsValidator(EXPRESSION_VALIDATOR));
}

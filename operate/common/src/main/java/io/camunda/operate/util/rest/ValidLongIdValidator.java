/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.rest;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidLongIdValidator implements ConstraintValidator<ValidLongId, String> {
  @Override
  public boolean isValid(String input, ConstraintValidatorContext constraintValidatorContext) {
    try {
      return Long.parseLong(input) >= 0L;
    } catch (NumberFormatException ex) {
      return false;
    }
  }
}

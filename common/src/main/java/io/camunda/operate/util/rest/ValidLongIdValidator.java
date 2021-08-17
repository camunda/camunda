/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.util.rest;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

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

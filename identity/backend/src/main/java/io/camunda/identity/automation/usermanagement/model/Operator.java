/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. Licensed under a proprietary license. See the
 * License.txt file for more information. You may not use this file except in compliance with the
 * proprietary license.
 */
package io.camunda.identity.automation.usermanagement.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.stream.Stream;

public enum Operator {
  CONTAINS,
  EQUALS;

  @JsonCreator
  public static Operator fromString(final String value) {
    return Stream.of(Operator.values())
        .filter(evaluationMethod -> evaluationMethod.name().equals(value.toUpperCase()))
        .findFirst()
        .orElse(null);
  }
}

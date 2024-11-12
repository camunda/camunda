/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import static io.camunda.optimize.dto.optimize.ReportConstants.BOOLEAN_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DATE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.DOUBLE_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.INTEGER_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.JSON_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.LONG_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.OBJECT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.SHORT_TYPE;
import static io.camunda.optimize.dto.optimize.ReportConstants.STRING_TYPE;
import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public enum VariableType {
  STRING(STRING_TYPE),
  SHORT(SHORT_TYPE),
  LONG(LONG_TYPE),
  DOUBLE(DOUBLE_TYPE),
  INTEGER(INTEGER_TYPE),
  BOOLEAN(BOOLEAN_TYPE),
  DATE(DATE_TYPE),
  OBJECT(OBJECT_TYPE),
  JSON(JSON_TYPE);

  private static final Set<VariableType> NUMERIC_TYPES = Set.of(INTEGER, SHORT, LONG, DOUBLE);
  private static final Map<String, VariableType> BY_LOWER_CASE_ID_MAP =
      Stream.of(VariableType.values())
          .collect(toMap(type -> type.getId().toLowerCase(Locale.ENGLISH), type -> type));

  private final String id;

  VariableType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  public static VariableType getTypeForId(final String id) {
    return Optional.ofNullable(id)
        .map(String::toLowerCase)
        .map(BY_LOWER_CASE_ID_MAP::get)
        .orElse(null);
  }

  public static Set<VariableType> getNumericTypes() {
    return NUMERIC_TYPES;
  }
}

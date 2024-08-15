/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util;

import static io.camunda.optimize.dto.optimize.ReportConstants.ALL_PRIMITIVE_PROCESS_VARIABLE_TYPES;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import io.camunda.optimize.rest.optimize.dto.VariableDto;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class VariableTestUtil {

  private VariableTestUtil() {}

  public static Map<String, Object> createAllPrimitiveTypeVariables() {
    final Map<String, Object> variables = new HashMap<>();
    final int integer = 1;
    variables.put("stringVar", "aStringValue");
    variables.put("boolVar", true);
    variables.put("integerVar", integer);
    variables.put("shortVar", (short) integer);
    variables.put("longVar", 1L);
    variables.put("doubleVar", 1.1);
    variables.put("dateVar", new Date());
    return variables;
  }

  public static Map<String, Object> createAllPrimitiveVariableTypesWithNullValues() {
    final Map<String, Object> variables = new HashMap<>();
    for (final VariableType type : ALL_PRIMITIVE_PROCESS_VARIABLE_TYPES) {
      final String varName = String.format("%sVar", type.getId().toLowerCase(Locale.ENGLISH));
      variables.put(varName, new VariableDto().setType(type.getId()).setValue(null));
    }
    return variables;
  }
}

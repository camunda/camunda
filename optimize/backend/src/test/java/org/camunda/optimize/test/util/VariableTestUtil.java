/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util;

import static org.camunda.optimize.dto.optimize.ReportConstants.ALL_PRIMITIVE_PROCESS_VARIABLE_TYPES;

import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.rest.optimize.dto.VariableDto;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VariableTestUtil {

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

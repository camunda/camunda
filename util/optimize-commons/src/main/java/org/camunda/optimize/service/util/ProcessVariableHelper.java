/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Arrays;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.index.InstanceType.MULTIVALUE_FIELD_DATE;
import static org.camunda.optimize.service.es.schema.index.InstanceType.MULTIVALUE_FIELD_DOUBLE;
import static org.camunda.optimize.service.es.schema.index.InstanceType.MULTIVALUE_FIELD_LONG;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLES;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_TYPE;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;

public class ProcessVariableHelper {

  // first letter uppercase is used by VariableFilterDataDto json type info
  public static final String STRING_TYPE = "String";
  public static final String STRING_TYPE_LOWERCASE = "string";
  public static final String INTEGER_TYPE = "Integer";
  public static final String INTEGER_TYPE_LOWERCASE = "integer";
  public static final String SHORT_TYPE = "Short";
  public static final String SHORT_TYPE_LOWERCASE = "short";
  public static final String LONG_TYPE = "Long";
  public static final String LONG_TYPE_LOWERCASE = "long";
  public static final String DOUBLE_TYPE = "Double";
  public static final String DOUBLE_TYPE_LOWERCASE = "double";
  public static final String BOOLEAN_TYPE = "Boolean";
  public static final String BOOLEAN_TYPE_LOWERCASE = "boolean";
  public static final String DATE_TYPE = "Date";
  public static final String DATE_TYPE_LOWERCASE = "date";

  public static final VariableType[] ALL_SUPPORTED_VARIABLE_TYPES = VariableType.values();

  private ProcessVariableHelper() {
  }

  public static boolean isVariableTypeSupported(String variableTypeString) {
    return isVariableTypeSupported(VariableType.getTypeForId(variableTypeString));
  }

  public static boolean isVariableTypeSupported(VariableType variableType) {
    return Arrays.asList(ALL_SUPPORTED_VARIABLE_TYPES).contains(variableType);
  }

  public static String getNestedVariableNameField() {
    return VARIABLES + "." + VARIABLE_NAME;
  }

  public static String getNestedVariableIdField() {
    return VARIABLES + "." + VARIABLE_ID;
  }

  public static String getNestedVariableTypeField() {
    return VARIABLES + "." + VARIABLE_TYPE;
  }

  public static String getNestedVariableValueField() {
    return VARIABLES + "." + VARIABLE_VALUE;
  }

  public static String getNestedVariableValueFieldForType(final VariableType type) {
    switch (Optional.ofNullable(type).orElseThrow(() -> new IllegalArgumentException("No Type provided"))) {
      case BOOLEAN:
      case STRING:
        return getNestedVariableValueField();
      case DOUBLE:
        return getNestedVariableValueField() + "." + MULTIVALUE_FIELD_DOUBLE;
      case SHORT:
      case INTEGER:
      case LONG:
        return getNestedVariableValueField() + "." + MULTIVALUE_FIELD_LONG;
      case DATE:
        return getNestedVariableValueField() + "." + MULTIVALUE_FIELD_DATE;
      default:
        throw new IllegalArgumentException("Unhandled type: " + type);
    }
  }

}

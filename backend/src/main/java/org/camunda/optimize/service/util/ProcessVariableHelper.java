/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.BOOLEAN_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DATE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.DOUBLE_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.INTEGER_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.LONG_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.SHORT_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.STRING_VARIABLES;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_NAME;
import static org.camunda.optimize.service.es.schema.type.ProcessInstanceType.VARIABLE_VALUE;

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

  public static final String[] allVariableTypeFieldLabels =
    {STRING_VARIABLES, INTEGER_VARIABLES, LONG_VARIABLES, SHORT_VARIABLES,
      DOUBLE_VARIABLES, DATE_VARIABLES, BOOLEAN_VARIABLES};

  public static final VariableType[] ALL_SUPPORTED_VARIABLE_TYPES = VariableType.values();

  private static Map<VariableType, String> typeToVariableFieldLabel = initTypeToVariableFieldLabel();
  private static Map<String, VariableType> variableFieldLabelToType = initVariableFieldLabelToType();

  private ProcessVariableHelper() {
  }

  private static Map<VariableType,String> initTypeToVariableFieldLabel() {
    typeToVariableFieldLabel = new HashMap<>();
    typeToVariableFieldLabel.put(VariableType.STRING, STRING_VARIABLES);
    typeToVariableFieldLabel.put(VariableType.INTEGER, INTEGER_VARIABLES);
    typeToVariableFieldLabel.put(VariableType.SHORT, SHORT_VARIABLES);
    typeToVariableFieldLabel.put(VariableType.LONG, LONG_VARIABLES);
    typeToVariableFieldLabel.put(VariableType.DOUBLE, DOUBLE_VARIABLES);
    typeToVariableFieldLabel.put(VariableType.BOOLEAN, BOOLEAN_VARIABLES);
    typeToVariableFieldLabel.put(VariableType.DATE, DATE_VARIABLES);
    return typeToVariableFieldLabel;
  }

  private static Map<String, VariableType> initVariableFieldLabelToType() {
    variableFieldLabelToType = new HashMap<>();
    variableFieldLabelToType.put(STRING_VARIABLES, VariableType.STRING);
    variableFieldLabelToType.put(INTEGER_VARIABLES, VariableType.INTEGER);
    variableFieldLabelToType.put(SHORT_VARIABLES, VariableType.SHORT);
    variableFieldLabelToType.put(LONG_VARIABLES, VariableType.LONG);
    variableFieldLabelToType.put(DOUBLE_VARIABLES, VariableType.DOUBLE);
    variableFieldLabelToType.put(BOOLEAN_VARIABLES, VariableType.BOOLEAN);
    variableFieldLabelToType.put(DATE_VARIABLES, VariableType.DATE);
    return variableFieldLabelToType;
  }

  public static boolean isVariableTypeSupported(String variableTypeString) {
    return isVariableTypeSupported(VariableType.getTypeForId(variableTypeString));
  }

  public static boolean isVariableTypeSupported(VariableType variableType) {
    return typeToVariableFieldLabel.containsKey(variableType);
  }

  public static String variableTypeToFieldLabel(String variableTypeString) {
    return typeToVariableFieldLabel.get(VariableType.getTypeForId(variableTypeString));
  }

  public static String variableTypeToFieldLabel(VariableType variableType) {
    return typeToVariableFieldLabel.get(variableType);
  }

  public static VariableType fieldLabelToVariableType(String variableFieldLabel) {
    return variableFieldLabelToType.get(variableFieldLabel);
  }

  public static String[] getAllVariableTypeFieldLabels() {
    return allVariableTypeFieldLabels;
  }

  public static String getNestedVariableNameFieldLabel(String variableFieldLabel) {
    return variableFieldLabel + "." + VARIABLE_NAME;
  }

  public static String getNestedVariableValueFieldLabel(String variableFieldLabel) {
    return variableFieldLabel + "." + VARIABLE_VALUE;
  }

  public static String getNestedVariableNameFieldLabelForType(VariableType variableType) {
    return getNestedVariableNameFieldLabel(
      typeToVariableFieldLabel.get(variableType)
    );
  }

  public static String getNestedVariableValueFieldLabelForType(VariableType variableType) {
    return getNestedVariableValueFieldLabel(
      typeToVariableFieldLabel.get(variableType)
    );
  }

}

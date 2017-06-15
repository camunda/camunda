package org.camunda.optimize.service.util;

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

public class VariableHelper {

  public static final String STRING_TYPE = "String";
  public static final String INTEGER_TYPE = "Integer";
  public static final String SHORT_TYPE = "Short";
  public static final String LONG_TYPE = "Long";
  public static final String DOUBLE_TYPE = "Double";
  public static final String BOOLEAN_TYPE = "Boolean";
  public static final String DATE_TYPE = "Date";

  public static final String[] allVariableTypeFieldLabels =
    {STRING_VARIABLES, INTEGER_VARIABLES, LONG_VARIABLES, SHORT_VARIABLES,
      DOUBLE_VARIABLES, DATE_VARIABLES, BOOLEAN_VARIABLES};

  public static final String[] ALL_SUPPORTED_VARIABLE_TYPES =
    {STRING_TYPE, INTEGER_TYPE, LONG_TYPE, SHORT_TYPE,
      DOUBLE_TYPE, DATE_TYPE, BOOLEAN_TYPE};

  private static Map<String, String> typeToVariableFieldLabel = initTypeToVariableFieldLabel();
  private static Map<String, String> variableFieldLabelToType = initVariableFieldLabelToType();

  public static boolean isStringType(String type) {
    return type.toLowerCase().equals(STRING_TYPE.toLowerCase());
  }

  public static boolean isIntegerType(String type) {
    return type.toLowerCase().equals(INTEGER_TYPE.toLowerCase());
  }

  public static boolean isShortType(String type) {
    return type.toLowerCase().equals(SHORT_TYPE.toLowerCase());
  }

  public static boolean isLongType(String type) {
    return type.toLowerCase().equals(LONG_TYPE.toLowerCase());
  }

  public static boolean isDoubleType(String type) {
    return type.toLowerCase().equals(DOUBLE_TYPE.toLowerCase());
  }

  public static boolean isBooleanType(String type) {
    return type.toLowerCase().equals(BOOLEAN_TYPE.toLowerCase());
  }

  public static boolean isDateType(String type) {
    return type.toLowerCase().equals(DATE_TYPE.toLowerCase());
  }

  private static Map<String,String> initTypeToVariableFieldLabel() {
    typeToVariableFieldLabel = new HashMap<>();
    typeToVariableFieldLabel.put("string", STRING_VARIABLES);
    typeToVariableFieldLabel.put("integer", INTEGER_VARIABLES);
    typeToVariableFieldLabel.put("short", SHORT_VARIABLES);
    typeToVariableFieldLabel.put("long", LONG_VARIABLES);
    typeToVariableFieldLabel.put("double", DOUBLE_VARIABLES);
    typeToVariableFieldLabel.put("boolean", BOOLEAN_VARIABLES);
    typeToVariableFieldLabel.put("date", DATE_VARIABLES);
    return typeToVariableFieldLabel;
  }

  private static Map<String, String> initVariableFieldLabelToType() {
    variableFieldLabelToType = new HashMap<>();
    variableFieldLabelToType.put(STRING_VARIABLES, STRING_TYPE);
    variableFieldLabelToType.put(INTEGER_VARIABLES, INTEGER_TYPE);
    variableFieldLabelToType.put(SHORT_VARIABLES, SHORT_TYPE);
    variableFieldLabelToType.put(LONG_VARIABLES, LONG_TYPE);
    variableFieldLabelToType.put(DOUBLE_VARIABLES, DOUBLE_TYPE);
    variableFieldLabelToType.put(BOOLEAN_VARIABLES, BOOLEAN_TYPE);
    variableFieldLabelToType.put(DATE_VARIABLES, DATE_TYPE);
    return variableFieldLabelToType;
  }

  public static boolean isVariableTypeSupported(String variableType) {
    return typeToVariableFieldLabel.containsKey(variableType.toLowerCase());
  }

  public static String variableTypeToFieldLabel(String variableType) {
    return typeToVariableFieldLabel.get(variableType.toLowerCase());
  }

  public static String fieldLabelToVariableType(String variableFieldLabel) {
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

  public static String getNestedVariableNameFieldLabelForType(String variableType) {
    return getNestedVariableNameFieldLabel(
      typeToVariableFieldLabel.get(variableType.toLowerCase())
    );
  }

  public static String getNestedVariableValueFieldLabelForType(String variableType) {
    return getNestedVariableValueFieldLabel(
      typeToVariableFieldLabel.get(variableType.toLowerCase())
    );
  }
}

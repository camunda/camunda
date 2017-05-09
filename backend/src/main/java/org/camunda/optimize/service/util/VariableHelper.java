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

public class VariableHelper {

  public static final String STRING_TYPE = "String";
  public static final String INTEGER_TYPE = "Integer";
  public static final String SHORT_TYPE = "Short";
  public static final String LONG_TYPE = "Long";
  public static final String DOUBLE_TYPE = "Double";
  public static final String BOOLEAN_TYPE = "Boolean";
  public static final String DATE_TYPE = "Date";

  private static Map<String,String> typeToVariableFieldLabel = init();

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

  private static Map<String,String> init() {
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

  public static boolean isVariableTypeSupported(String variableType) {
    return typeToVariableFieldLabel.containsKey(variableType.toLowerCase());
  }

  public static String variableTypeToFieldLabel(String variableType) {
    return typeToVariableFieldLabel.get(variableType.toLowerCase());
  }
}

package org.camunda.optimize.service.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.INPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.MULTIVALUE_FIELD_DATE;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.MULTIVALUE_FIELD_DOUBLE;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.MULTIVALUE_FIELD_LONG;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.OUTPUTS;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.VARIABLE_CLAUSE_ID;
import static org.camunda.optimize.service.es.schema.type.DecisionInstanceType.VARIABLE_VALUE;

public class DecisionVariableHelper {
  public static final String STRING_TYPE = "string";
  public static final String INTEGER_TYPE = "integer";
  public static final String SHORT_TYPE = "short";
  public static final String LONG_TYPE = "long";
  public static final String DOUBLE_TYPE = "double";
  public static final String BOOLEAN_TYPE = "boolean";
  public static final String DATE_TYPE = "date";

  private static final List<String> MULTIVALUE_TYPE_FIELDS = Collections.unmodifiableList(Arrays.asList(
    DATE_TYPE, DOUBLE_TYPE, LONG_TYPE
  ));

  private DecisionVariableHelper() {
  }

  private static String getVariableValueField(final String variablePath) {
    return variablePath + "." + VARIABLE_VALUE;
  }

  public static String getInputVariableValueFieldForType(final String type) {
    return getVariableValueFieldForType(INPUTS, type);
  }

  public static String getOutputVariableValueFieldForType(final String type) {
    return getVariableValueFieldForType(OUTPUTS, type);
  }

  public static List<String> getVariableMultivalueFields() {
    return MULTIVALUE_TYPE_FIELDS;
  }

  public static String getVariableValueFieldForType(final String variablePath, final String type) {
    switch (Optional.ofNullable(type).map(String::toLowerCase).orElse("null")) {
      case BOOLEAN_TYPE:
      case STRING_TYPE:
        return getVariableValueField(variablePath);
      case DOUBLE_TYPE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_DOUBLE;
      case SHORT_TYPE:
      case INTEGER_TYPE:
      case LONG_TYPE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_LONG;
      case DATE_TYPE:
        return getVariableValueField(variablePath) + "." + MULTIVALUE_FIELD_DATE;
      default:
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }

  public static String getInputVariableIdField() {
    return getVariableIdField(INPUTS);
  }

  public static String getOutputVariableIdField() {
    return getVariableIdField(INPUTS);
  }

  public static String getVariableIdField(final String variablePath) {
    return variablePath + "." + VARIABLE_CLAUSE_ID;
  }

}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.util;

import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.MULTIVALUE_FIELD_DATE;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.MULTIVALUE_FIELD_DOUBLE;
import static io.camunda.optimize.service.db.schema.index.AbstractInstanceIndex.MULTIVALUE_FIELD_LONG;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLES;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_ID;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_NAME;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_TYPE;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.VARIABLE_VALUE;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Optional;

public final class ProcessVariableHelper {

  private ProcessVariableHelper() {}

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

  public static String getValueSearchField(final String searchFieldName) {
    return getNestedVariableValueField() + "." + searchFieldName;
  }

  public static String buildWildcardQuery(final String valueFilter) {
    return "*" + valueFilter + "*";
  }

  public static String getNestedVariableValueFieldForType(final VariableType type) {
    return switch (Optional.ofNullable(type)
        .orElseThrow(() -> new IllegalArgumentException("No Type provided"))) {
      case BOOLEAN, STRING, OBJECT -> getNestedVariableValueField();
      case DOUBLE -> getNestedVariableValueField() + "." + MULTIVALUE_FIELD_DOUBLE;
      case SHORT, INTEGER, LONG -> getNestedVariableValueField() + "." + MULTIVALUE_FIELD_LONG;
      case DATE -> getNestedVariableValueField() + "." + MULTIVALUE_FIELD_DATE;
      default -> throw new IllegalArgumentException("Unhandled type: " + type);
    };
  }
}

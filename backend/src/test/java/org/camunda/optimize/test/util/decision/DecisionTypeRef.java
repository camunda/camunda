/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.util.decision;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum DecisionTypeRef {
  STRING("string"),
  LONG("long"),
  DOUBLE("double"),
  INTEGER("integer"),
  BOOLEAN("boolean"),
  DATE("date"),
  ;

  private static final Set<DecisionTypeRef> NUMERIC_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
    INTEGER, LONG, DOUBLE
  )));

  private final String id;

  DecisionTypeRef(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  public static Set<DecisionTypeRef> getNumericTypes() {
    return NUMERIC_TYPES;
  }

}
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.util.decision;

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
  DATE("date");

  private static final Set<DecisionTypeRef> NUMERIC_TYPES =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(INTEGER, LONG, DOUBLE)));

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

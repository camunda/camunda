/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.util.decision;

public enum DecisionTypeRef {
  STRING("string"),
  LONG("long"),
  DOUBLE("double"),
  INTEGER("integer"),
  BOOLEAN("boolean"),
  DATE("date"),
  ;

  private final String id;

  DecisionTypeRef(final String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
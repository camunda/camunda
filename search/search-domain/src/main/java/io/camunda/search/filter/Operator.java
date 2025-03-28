/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

public enum Operator {
  EQUALS("eq"),
  NOT_EQUALS("neq"),
  EXISTS("exists"),
  NOT_EXISTS,
  GREATER_THAN("gt"),
  GREATER_THAN_EQUALS("gte"),
  LOWER_THAN("lt"),
  LOWER_THAN_EQUALS("lte"),
  IN("in"),
  NIN("nin"),
  LIKE("like");

  private final String value;

  Operator() {
    value = null;
  }

  Operator(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}

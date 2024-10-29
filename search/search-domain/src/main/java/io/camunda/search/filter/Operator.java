/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

public enum Operator {
  EQUALS,
  NOT_EQUALS,
  EXISTS,
  NOT_EXISTS,
  GREATER_THAN,
  GREATER_THAN_EQUALS,
  LOWER_THAN,
  LOWER_THAN_EQUALS,
  IN,
  LIKE,
}

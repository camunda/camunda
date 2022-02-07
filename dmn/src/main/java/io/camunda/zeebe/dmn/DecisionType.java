/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn;

/** The type of decision (i.e. its decision logic). */
public enum DecisionType {
  DECISION_TABLE,
  LITERAL_EXPRESSION,
  CONTEXT,
  INVOCATION,
  LIST,
  RELATION,
  UNKNOWN
}

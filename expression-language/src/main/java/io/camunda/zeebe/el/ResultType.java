/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el;

/** The possible types of an evaluation result. */
public enum ResultType {
  NULL,
  BOOLEAN,
  NUMBER,
  STRING,
  DURATION,
  PERIOD,
  DATE_TIME,
  ARRAY,
  OBJECT
}

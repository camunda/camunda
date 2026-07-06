/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dimension;

/**
 * The value type of a {@link DimensionColumn}. {@link #STRING} is a bounded, indexable identifier;
 * {@link #TEXT} is a large character payload (e.g. a process's BPMN XML) that maps to a large-text
 * column (CLOB/TEXT), read and written as a {@code String} — never a grouping key. The rest are the
 * scalar types dimensions take.
 */
public enum DimensionType {
  STRING,
  TEXT,
  LONG,
  INT,
  BOOLEAN
}

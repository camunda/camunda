/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

/** What a key index does when its column family has more keys than the cap. */
public enum KeyIndexPolicy {
  /** Drops the index until the next restart. */
  DISABLE,
  /** Keeps the lowest keys: the index covers every key below a bound. */
  KEEP_LOWEST,
  /** Keeps the highest keys: the index covers every key above a bound. */
  KEEP_HIGHEST;

  /** Whether the covered keys are the ones at the start of a forward scan. */
  boolean keepsLowest() {
    return this == KEEP_LOWEST;
  }
}

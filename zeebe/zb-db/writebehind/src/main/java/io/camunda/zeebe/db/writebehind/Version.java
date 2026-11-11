/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import org.jspecify.annotations.Nullable;

final class Version<V> {

  private final @Nullable V value;
  private final long seq;

  Version(final @Nullable V value, final long seq) {
    this.value = value;
    this.seq = seq;
  }

  /** The value, or null for a delete. Shared: callers must copy it before handing it out. */
  @Nullable V value() {
    return value;
  }

  /** The commit sequence of the transaction that wrote this version. */
  long seq() {
    return seq;
  }

  boolean isTombstone() {
    return value == null;
  }
}

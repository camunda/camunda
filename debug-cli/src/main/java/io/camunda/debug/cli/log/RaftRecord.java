/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.debug.cli.log;

public class RaftRecord implements PersistedRecord {
  private final long index;
  private final long term;

  public RaftRecord(final long index, final long term) {
    this.index = index;
    this.term = term;
  }

  @Override
  public long index() {
    return index;
  }

  @Override
  public long term() {
    return term;
  }

  @Override
  public String asColumnString() {
    return index + " " + term + " ";
  }

  @Override
  public String toString() {
    return String.format("{\"index\":%d,\"term\":%d}", index, term);
  }
}

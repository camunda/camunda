/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_3;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import org.agrona.collections.MutableInteger;

public class MemoryChecker {

  private static final int DEFAULT_TRANSACTION_MEMORY_LIMIT = 50 * 1024 * 1024; // 50MB
  private final MutableInteger memoryUsage = new MutableInteger();
  private final int limit;

  public MemoryChecker() {
    this(DEFAULT_TRANSACTION_MEMORY_LIMIT);
  }

  public MemoryChecker(final int limit) {
    this.limit = limit;
  }

  public boolean isBelowLimit() {
    return memoryUsage.get() < limit;
  }

  public boolean isBelowLimit(final DbKey key, final DbValue value) {
    return add(key, value).isBelowLimit();
  }

  public MemoryChecker add(final DbKey key, final DbValue value) {
    memoryUsage.addAndGet(key.getLength() + value.getLength());
    return this;
  }

  public void reset() {
    memoryUsage.set(0);
  }
}

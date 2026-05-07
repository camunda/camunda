/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import org.jspecify.annotations.Nullable;

/**
 * Internal support for ordered raw iteration without materializing entries into an intermediate
 * map. Callers must ensure the backing transaction remains open for the full iterator lifetime.
 */
public interface RawEntryIteratorProvider {

  RawEntryIterator newRawIterator(@Nullable DbKey startAt, DbKey prefix, boolean reverse);

  interface RawEntryIterator extends AutoCloseable {

    boolean isValid();

    byte[] key();

    void writeValueInto(DbValue target);

    void next();

    @Override
    default void close() {}
  }
}

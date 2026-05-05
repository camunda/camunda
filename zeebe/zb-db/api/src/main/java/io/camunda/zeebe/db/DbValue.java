/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.util.Copyable;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * The value which should be stored together with a key.
 *
 * <p>Extends {@link Copyable} for zero-serialization in-memory storage. Implementations are
 * expected to provide explicit {@link #copyTo(DbValue)} and {@link #newInstance()} methods.
 */
public interface DbValue extends BufferWriter, BufferReader, Copyable<DbValue> {

  @Override
  void copyTo(DbValue target);

  @Override
  DbValue newInstance();
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.ZeebeDbTransaction;
import org.jspecify.annotations.Nullable;

/**
 * Byte-level view of a transaction: raw keys and values, already prefixed with the column family.
 * Kept separate from the public {@link ZeebeDbTransaction} so that code outside the column families
 * cannot write raw bytes past the key prefix, the consistency checks and the metrics.
 *
 * <p>Reads see the transaction's own uncommitted writes.
 */
public interface ZeebeTransaction extends ZeebeDbTransaction {

  void put(byte[] key, int keyLength, byte[] value, int valueLength) throws Exception;

  byte @Nullable [] get(byte[] key, int keyLength) throws Exception;

  void delete(byte[] key, int keyLength) throws Exception;

  /**
   * Iterates over the committed state merged with this transaction's own writes.
   *
   * @param prefix the key prefix the caller scans; it tells implementations which part of the key
   *     space the iterator has to cover. Callers still check each key against the prefix.
   * @param prefixLength number of bytes of {@code prefix} that are used
   */
  DbIterator newIterator(byte[] prefix, int prefixLength);

  void resetTransaction();

  boolean isInCurrentTransaction();

  void commitInternal() throws Exception;

  void rollbackInternal() throws Exception;
}

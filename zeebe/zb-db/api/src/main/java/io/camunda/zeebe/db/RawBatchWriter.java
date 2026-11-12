/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import java.util.List;

/**
 * A {@link ZeebeDb} that takes the write-behind cache's writes as raw bytes, one batch at a time,
 * with the log positions that wrote them. It's optional: the cache writes to a storage without it
 * through column families.
 *
 * <p>The cache calls it from one thread only.
 */
public interface RawBatchWriter {

  /**
   * Writes the batch in one transaction, and records that the state now stands at {@code position}.
   *
   * @param writes sorted by column family number, then by key as unsigned bytes; one per key
   * @param position the log position of the last transaction in the batch
   */
  void write(List<RawWrite> writes, long position);
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.publisher;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** The journal of one partition, as far as it is committed, read in index order. */
public interface EntrySource extends AutoCloseable {

  /** The index of the first entry it holds, or of the entry it will hold first if it holds none. */
  long firstIndex() throws IOException;

  /** Makes {@link #next} start at the entry at {@code index}, which is at least the first index. */
  void seek(long index) throws IOException;

  /** The next committed entry, or null if there is none yet. */
  @Nullable SourcedEntry next() throws IOException;

  @Override
  default void close() throws IOException {}
}

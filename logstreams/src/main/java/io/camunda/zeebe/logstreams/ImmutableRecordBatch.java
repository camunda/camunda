/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams;

import java.util.Iterator;
import java.util.List;

/**
 * Represents an unmodifiable batch of records, which extends the {@link Iterable<
 * ImmutableRecordBatchEntry >} in order to make sure that the contained entries can be accessed.
 */
public interface ImmutableRecordBatch extends Iterable<ImmutableRecordBatchEntry> {
  int size();

  boolean isEmpty();

  record Impl(List<ImmutableRecordBatchEntry> entries, int size) implements ImmutableRecordBatch {

    @Override
    public boolean isEmpty() {
      return entries.isEmpty();
    }

    @Override
    public Iterator<ImmutableRecordBatchEntry> iterator() {
      return entries.iterator();
    }
  }
}

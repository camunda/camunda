/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.util;

import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.LogStorage;

public final class SyncLogStreamBuilder implements LogStreamBuilder {
  private final LogStreamBuilder delegate;

  SyncLogStreamBuilder() {
    this(LogStream.builder());
  }

  SyncLogStreamBuilder(final LogStreamBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  public SyncLogStreamBuilder withMaxFragmentSize(final int maxFragmentSize) {
    delegate.withMaxFragmentSize(maxFragmentSize);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withLogStorage(final LogStorage logStorage) {
    delegate.withLogStorage(logStorage);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withPartitionId(final int partitionId) {
    delegate.withPartitionId(partitionId);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withLogName(final String logName) {
    delegate.withLogName(logName);
    return this;
  }

  @Override
  public LogStream build() {
    return delegate.build();
  }

  public SyncLogStream buildSyncStream() {
    final var logStream = delegate.build();
    return new SyncLogStream(logStream);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.util;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import java.time.InstantSource;

public final class TestLogStreamBuilder implements LogStreamBuilder {
  private final LogStreamBuilder delegate;

  TestLogStreamBuilder() {
    this(LogStream.builder());
  }

  TestLogStreamBuilder(final LogStreamBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  public TestLogStreamBuilder withMaxFragmentSize(final int maxFragmentSize) {
    delegate.withMaxFragmentSize(maxFragmentSize);
    return this;
  }

  @Override
  public TestLogStreamBuilder withLogStorage(final LogStorage logStorage) {
    delegate.withLogStorage(logStorage);
    return this;
  }

  @Override
  public TestLogStreamBuilder withPartitionId(final int partitionId) {
    delegate.withPartitionId(partitionId);
    return this;
  }

  @Override
  public TestLogStreamBuilder withLogName(final String logName) {
    delegate.withLogName(logName);
    return this;
  }

  @Override
  public TestLogStreamBuilder withClock(final InstantSource clock) {
    delegate.withClock(clock);
    return this;
  }

  @Override
  public LogStreamBuilder withRequestLimit(final Limit requestLimit) {
    delegate.withRequestLimit(requestLimit);
    return this;
  }

  @Override
  public LogStreamBuilder withWriteRateLimit(final RateLimit writeRateLimiter) {
    delegate.withWriteRateLimit(writeRateLimiter);
    return this;
  }

  @Override
  public TestLogStream build() {
    return new TestLogStream(delegate.build());
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.log;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.logstreams.impl.flowcontrol.RateLimit;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.InstantSource;

/** Builder pattern for the {@link LogStream} */
public interface LogStreamBuilder {

  /**
   * The actor scheduler to use for the {@link LogStream} and its child actors
   *
   * @param actorSchedulingService the scheduler to use
   * @return this builder
   */
  LogStreamBuilder withActorSchedulingService(ActorSchedulingService actorSchedulingService);

  /**
   * The maximum fragment size read from the shared write buffer; this should be aligned with the
   * maximum underlying storage block size.
   *
   * @param maxFragmentSize the maximum fragment size in bytes
   * @return this builder
   */
  LogStreamBuilder withMaxFragmentSize(int maxFragmentSize);

  /**
   * The underlying log storage to read from/write to.
   *
   * @param logStorage the underlying log storage
   * @return this builder
   */
  LogStreamBuilder withLogStorage(LogStorage logStorage);

  /**
   * The partition ID - primarily used for contextualizing the different log stream components
   *
   * @param partitionId the log stream's partition ID
   * @return this builder
   */
  LogStreamBuilder withPartitionId(int partitionId);

  /**
   * The log stream name - primarily used for contextualizing as well, e.g. loggers, actor name,
   * etc.
   *
   * @param logName the current log name
   * @return this builder
   */
  LogStreamBuilder withLogName(String logName);

  /** Clock used to assign record timestamps */
  LogStreamBuilder withClock(InstantSource clock);

  LogStreamBuilder withRequestLimit(Limit requestLimit);

  LogStreamBuilder withWriteRateLimit(RateLimit writeRateLimit);

  /**
   * Sets the meter registry to collect metrics on.
   *
   * @param meterRegistry the new meter registry to collect metrics on
   * @return this builder
   */
  LogStreamBuilder withMeterRegistry(final MeterRegistry meterRegistry);

  /**
   * Returns a future which, when completed, contains a log stream that can be read from/written to.
   *
   * @return a future which on complete contains the log stream
   */
  LogStream build();
}

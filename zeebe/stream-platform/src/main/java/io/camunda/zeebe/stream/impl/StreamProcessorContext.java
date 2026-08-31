/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import static java.util.Objects.requireNonNull;

import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.stream.api.state.MutableLastProcessedPositionState;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

public final class StreamProcessorContext implements ReadonlyStreamProcessorContext {

  public static final int DEFAULT_MAX_COMMANDS_IN_BATCH = 100;
  public static final int DEFAULT_MAX_RECOVERABLE_RETRIES = 1000;
  private static final StreamProcessorListener NOOP_LISTENER = processedCommand -> {};
  private @Nullable ActorControl actor;
  private @Nullable LogStream logStream;
  private @Nullable PartitionId partitionId;
  private @Nullable LogStreamReader logStreamReader;
  private @Nullable LogStreamReader processingLogStreamReader;
  private @Nullable RecordValues recordValues;
  private @Nullable TransactionContext transactionContext;

  private @Nullable BooleanSupplier abortCondition;
  private StreamProcessorListener streamProcessorListener = NOOP_LISTENER;

  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private @Nullable ProcessingScheduleService processingScheduleService;
  private @Nullable MutableLastProcessedPositionState lastProcessedPositionState;

  private @Nullable LogStreamWriter logStreamWriter;
  private @Nullable CommandResponseWriter commandResponseWriter;
  private @Nullable InterPartitionCommandSender partitionCommandSender;

  // this is accessed outside, which is why we need to make sure that it is thread-safe
  private volatile StreamProcessor.Phase phase = Phase.INITIAL;
  private @Nullable KeyGeneratorControls keyGeneratorControls;
  private int maxCommandsInBatch = DEFAULT_MAX_COMMANDS_IN_BATCH;
  private int maxRecoverableRetries = DEFAULT_MAX_RECOVERABLE_RETRIES;
  private EventFilter processingFilter = e -> true;
  private @Nullable ControllableStreamClock clock;
  private @Nullable MeterRegistry meterRegistry;
  private Duration scheduledTaskCheckInterval = Duration.ofSeconds(1);

  public StreamProcessorContext actor(final ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public StreamProcessorContext scheduleService(final ProcessingScheduleService scheduleService) {
    processingScheduleService = scheduleService;
    return this;
  }

  @Override
  public ProcessingScheduleService getScheduleService() {
    return requireNonNull(processingScheduleService);
  }

  @Override
  public int getPartitionId() {
    return getLogStream().getPartitionId();
  }

  @Override
  public ControllableStreamClock getClock() {
    return requireNonNull(clock);
  }

  /**
   * Returns the composite partition id (partition group + number). Falls back to the default
   * partition group when none was configured, so callers that only set up a log stream still get a
   * usable value.
   */
  public PartitionId partitionId() {
    return partitionId != null
        ? partitionId
        : new PartitionId(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID, getPartitionId());
  }

  public StreamProcessorContext partitionId(final PartitionId partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public StreamProcessorContext clock(final ControllableStreamClock clock) {
    this.clock = requireNonNull(clock);
    return this;
  }

  public LogStream getLogStream() {
    return requireNonNull(logStream);
  }

  public MutableLastProcessedPositionState getLastProcessedPositionState() {
    return requireNonNull(lastProcessedPositionState);
  }

  StreamProcessorContext listener(final StreamProcessorListener streamProcessorListener) {
    this.streamProcessorListener = streamProcessorListener;
    return this;
  }

  public StreamProcessorContext logStream(final LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public StreamProcessorContext logStreamReader(final LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  /**
   * Sets the reader that is used for processing records. In contrast to {@link
   * #logStreamReader(LogStreamReader)}, this reader may also read records that have not been
   * committed yet.
   */
  public StreamProcessorContext processingLogStreamReader(
      final LogStreamReader processingLogStreamReader) {
    this.processingLogStreamReader = processingLogStreamReader;
    return this;
  }

  public StreamProcessorContext eventCache(final RecordValues recordValues) {
    this.recordValues = recordValues;
    return this;
  }

  public StreamProcessorContext keyGeneratorControls(
      final KeyGeneratorControls keyGeneratorControls) {
    this.keyGeneratorControls = keyGeneratorControls;
    return this;
  }

  public StreamProcessorContext lastProcessedPositionState(
      final MutableLastProcessedPositionState lastProcessedPositionState) {
    this.lastProcessedPositionState = lastProcessedPositionState;
    return this;
  }

  public StreamProcessorContext transactionContext(final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    return this;
  }

  public StreamProcessorContext abortCondition(final BooleanSupplier abortCondition) {
    this.abortCondition = abortCondition;
    return this;
  }

  public StreamProcessorContext commandResponseWriter(
      final CommandResponseWriter commandResponseWriter) {
    this.commandResponseWriter = commandResponseWriter;
    return this;
  }

  public StreamProcessorContext processorMode(final StreamProcessorMode streamProcessorMode) {
    this.streamProcessorMode = streamProcessorMode;
    return this;
  }

  public KeyGeneratorControls getKeyGeneratorControls() {
    return requireNonNull(keyGeneratorControls);
  }

  public ActorControl getActor() {
    return requireNonNull(actor);
  }

  /**
   * Returns the reader that replay reads committed records from.
   *
   * <p>Only valid while replay is running. In {@link StreamProcessorMode#PROCESSING} the stream
   * processor closes this reader once replay completes, so that it stops pinning log positions that
   * nothing needs any more. Consumers that outlive replay must use {@link
   * #getProcessingLogStreamReader()}.
   */
  public LogStreamReader getLogStreamReader() {
    return requireNonNull(logStreamReader);
  }

  /**
   * Returns the reader used for processing records, which may also read uncommitted records.
   *
   * <p>Only set in {@link StreamProcessorMode#PROCESSING}, because replay never reads uncommitted
   * records.
   */
  public LogStreamReader getProcessingLogStreamReader() {
    return requireNonNull(processingLogStreamReader);
  }

  public RecordValues getRecordValues() {
    return requireNonNull(recordValues);
  }

  public TransactionContext getTransactionContext() {
    return requireNonNull(transactionContext);
  }

  public BooleanSupplier getAbortCondition() {
    return requireNonNull(abortCondition);
  }

  public StreamProcessorListener getStreamProcessorListener() {
    return streamProcessorListener;
  }

  public StreamProcessorMode getProcessorMode() {
    return streamProcessorMode;
  }

  public void logStreamWriter(final LogStreamWriter writer) {
    logStreamWriter = writer;
  }

  public LogStreamWriter getLogStreamWriter() {
    return requireNonNull(logStreamWriter);
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return requireNonNull(commandResponseWriter);
  }

  public @Nullable InterPartitionCommandSender getPartitionCommandSender() {
    return partitionCommandSender;
  }

  public void partitionCommandSender(final InterPartitionCommandSender partitionCommandSender) {
    this.partitionCommandSender = partitionCommandSender;
  }

  public Phase getStreamProcessorPhase() {
    return phase;
  }

  public void streamProcessorPhase(final Phase phase) {
    this.phase = phase;
  }

  public StreamProcessorContext maxCommandsInBatch(final int maxCommandsInBatch) {
    this.maxCommandsInBatch = maxCommandsInBatch;
    return this;
  }

  public int getMaxCommandsInBatch() {
    return maxCommandsInBatch;
  }

  public StreamProcessorContext maxRecoverableRetries(final int maxRecoverableRetries) {
    this.maxRecoverableRetries = maxRecoverableRetries;
    return this;
  }

  public int getMaxRecoverableRetries() {
    return maxRecoverableRetries;
  }

  public EventFilter processingFilter() {
    return processingFilter;
  }

  public StreamProcessorContext processingFilter(final EventFilter processingFilter) {
    this.processingFilter = processingFilter;
    return this;
  }

  public StreamProcessorContext meterRegistry(final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    return this;
  }

  public MeterRegistry getMeterRegistry() {
    return requireNonNull(meterRegistry);
  }

  public Duration getScheduledTaskCheckInterval() {
    return scheduledTaskCheckInterval;
  }

  public StreamProcessorContext setScheduledTaskCheckInterval(
      final Duration scheduledTaskCheckInterval) {
    this.scheduledTaskCheckInterval = scheduledTaskCheckInterval;
    return this;
  }
}

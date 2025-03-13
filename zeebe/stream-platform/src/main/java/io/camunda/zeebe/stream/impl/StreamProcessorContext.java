/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

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
import io.camunda.zeebe.stream.api.scheduling.BackgroundTaskScheduleService;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.stream.api.state.MutableLastProcessedPositionState;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class StreamProcessorContext implements ReadonlyStreamProcessorContext {

  public static final int DEFAULT_MAX_COMMANDS_IN_BATCH = 100;
  private static final StreamProcessorListener NOOP_LISTENER = processedCommand -> {};
  private ActorControl actor;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private RecordValues recordValues;
  private TransactionContext transactionContext;

  private BooleanSupplier abortCondition;
  private StreamProcessorListener streamProcessorListener = NOOP_LISTENER;

  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private ProcessingScheduleService processingScheduleService;
  private BackgroundTaskScheduleService backgroundTaskScheduleService;
  private MutableLastProcessedPositionState lastProcessedPositionState;

  private LogStreamWriter logStreamWriter;
  private CommandResponseWriter commandResponseWriter;
  private InterPartitionCommandSender partitionCommandSender;

  // this is accessed outside, which is why we need to make sure that it is thread-safe
  private volatile StreamProcessor.Phase phase = Phase.INITIAL;
  private KeyGeneratorControls keyGeneratorControls;
  private int maxCommandsInBatch = DEFAULT_MAX_COMMANDS_IN_BATCH;
  private boolean enableAsyncScheduledTasks = true;
  private EventFilter processingFilter = e -> true;
  private ControllableStreamClock clock;
  private MeterRegistry meterRegistry;
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
    return processingScheduleService;
  }

  @Override
  public BackgroundTaskScheduleService getBackgroundService() {
    return backgroundTaskScheduleService;
  }

  @Override
  public int getPartitionId() {
    return getLogStream().getPartitionId();
  }

  @Override
  public boolean enableAsyncScheduledTasks() {
    return enableAsyncScheduledTasks;
  }

  @Override
  public ControllableStreamClock getClock() {
    return clock;
  }

  public StreamProcessorContext backgroundService(final BackgroundTaskScheduleService backgroundService) {
    backgroundTaskScheduleService = backgroundService;
    return this;
  }

  public StreamProcessorContext clock(final ControllableStreamClock clock) {
    this.clock = Objects.requireNonNull(clock);
    return this;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public MutableLastProcessedPositionState getLastProcessedPositionState() {
    return lastProcessedPositionState;
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
    return keyGeneratorControls;
  }

  public ActorControl getActor() {
    return actor;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public RecordValues getRecordValues() {
    return recordValues;
  }

  public TransactionContext getTransactionContext() {
    return transactionContext;
  }

  public BooleanSupplier getAbortCondition() {
    return abortCondition;
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
    return logStreamWriter;
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriter;
  }

  public InterPartitionCommandSender getPartitionCommandSender() {
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

  public StreamProcessorContext setEnableAsyncScheduledTasks(final boolean enabled) {
    enableAsyncScheduledTasks = enabled;
    return this;
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
    return meterRegistry;
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

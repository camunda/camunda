/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.api.CommandResponseWriter;
import io.camunda.zeebe.engine.api.InterPartitionCommandSender;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.RecordValues;
import io.camunda.zeebe.engine.state.KeyGeneratorControls;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.streamprocessor.StreamProcessor.Phase;
import io.camunda.zeebe.streamprocessor.state.MutableLastProcessedPositionState;
import java.util.function.BooleanSupplier;

public final class StreamProcessorContext implements ReadonlyStreamProcessorContext {

  private static final StreamProcessorListener NOOP_LISTENER =
      new StreamProcessorListener() {
        @Override
        public void onProcessed(final TypedRecord<?> processedCommand) {}

        @Override
        public void onSkipped(final LoggedEvent skippedRecord) {}
      };

  private ActorControl actor;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private RecordValues recordValues;
  private TransactionContext transactionContext;

  private BooleanSupplier abortCondition;
  private StreamProcessorListener streamProcessorListener = NOOP_LISTENER;

  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private ProcessingScheduleService processingScheduleService;
  private MutableLastProcessedPositionState lastProcessedPositionState;

  private LogStreamBatchWriter logStreamBatchWriter;
  private CommandResponseWriter commandResponseWriter;
  private InterPartitionCommandSender partitionCommandSender;

  // this is accessed outside, which is why we need to make sure that it is thread-safe
  private volatile StreamProcessor.Phase phase = Phase.INITIAL;
  private KeyGeneratorControls keyGeneratorControls;

  private ZeebeDb zeebeDb;
  private ActorSchedulingService actorSchedulingService;

  public StreamProcessorContext zeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public StreamProcessorContext actorSchedulingService(
      final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
    return this;
  }

  public StreamProcessorContext actor(final ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public StreamProcessorContext scheduleService(final ProcessingScheduleService scheduleService) {
    processingScheduleService = scheduleService;
    return this;
  }

  @Override
  public ActorSchedulingService getActorSchedulingService() {
    return actorSchedulingService;
  }

  @Override
  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  @Override
  public ProcessingScheduleService getScheduleService() {
    return processingScheduleService;
  }

  @Override
  public int getPartitionId() {
    return getLogStream().getPartitionId();
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

  public void logStreamBatchWriter(final LogStreamBatchWriter batchWriter) {
    logStreamBatchWriter = batchWriter;
  }

  public LogStreamBatchWriter getLogStreamBatchWriter() {
    return logStreamBatchWriter;
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
}

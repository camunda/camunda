/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.EventApplyingStateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriterImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.KeyGeneratorControls;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableLastProcessedPositionState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.util.sched.ActorControl;
import java.util.function.BooleanSupplier;

public final class ProcessingContext implements ReadonlyProcessingContext {

  private static final StreamProcessorListener NOOP_LISTENER = processedCommand -> {};

  private ActorControl actor;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private final Writers writers = new Writers();
  private RecordValues recordValues;
  private ZeebeDbState zeebeState;
  private TransactionContext transactionContext;
  private BooleanSupplier abortCondition;
  private StreamProcessorListener streamProcessorListener = NOOP_LISTENER;
  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private MutableLastProcessedPositionState lastProcessedPositionState;
  private TypedStreamWriter logStreamWriter;

  public ProcessingContext actor(final ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public ProcessingContext logStream(final LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ProcessingContext logStreamReader(final LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  public ProcessingContext eventCache(final RecordValues recordValues) {
    this.recordValues = recordValues;
    return this;
  }

  public ProcessingContext zeebeState(final ZeebeDbState zeebeState) {
    this.zeebeState = zeebeState;
    return this;
  }

  public ProcessingContext transactionContext(final TransactionContext transactionContext) {
    this.transactionContext = transactionContext;
    return this;
  }

  public ProcessingContext abortCondition(final BooleanSupplier abortCondition) {
    this.abortCondition = abortCondition;
    return this;
  }

  public ProcessingContext logStreamWriter(final TypedStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
    writers.setStream(logStreamWriter);
    return this;
  }

  public ProcessingContext commandResponseWriter(
      final CommandResponseWriter commandResponseWriter) {
    writers.setResponse(
        new TypedResponseWriterImpl(commandResponseWriter, getLogStream().getPartitionId()));
    return this;
  }

  public ProcessingContext listener(final StreamProcessorListener streamProcessorListener) {
    this.streamProcessorListener = streamProcessorListener;
    return this;
  }

  public ProcessingContext eventApplier(final EventApplier eventApplier) {
    writers.setState(new EventApplyingStateWriter(logStreamWriter, eventApplier));
    return this;
  }

  public ProcessingContext processorMode(final StreamProcessorMode streamProcessorMode) {
    this.streamProcessorMode = streamProcessorMode;
    return this;
  }

  public KeyGeneratorControls getKeyGeneratorControls() {
    return zeebeState.getKeyGeneratorControls();
  }

  public void lastProcessedPositionState(
      final MutableLastProcessedPositionState lastProcessedPositionState) {
    this.lastProcessedPositionState = lastProcessedPositionState;
  }

  public MutableLastProcessedPositionState getLastProcessedPositionState() {
    return lastProcessedPositionState;
  }

  @Override
  public ActorControl getActor() {
    return actor;
  }

  @Override
  public LogStream getLogStream() {
    return logStream;
  }

  @Override
  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  @Override
  public TypedStreamWriter getLogStreamWriter() {
    return logStreamWriter;
  }

  @Override
  public Writers getWriters() {

    return writers;
  }

  @Override
  public RecordValues getRecordValues() {
    return recordValues;
  }

  @Override
  public MutableZeebeState getZeebeState() {
    return zeebeState;
  }

  @Override
  public TransactionContext getTransactionContext() {
    return transactionContext;
  }

  @Override
  public BooleanSupplier getAbortCondition() {
    return abortCondition;
  }

  public StreamProcessorListener getStreamProcessorListener() {
    return streamProcessorListener;
  }

  public StreamProcessorMode getProcessorMode() {
    return streamProcessorMode;
  }
}

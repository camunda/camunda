/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.TypedStreamWriterProxy;
import io.camunda.zeebe.engine.processing.streamprocessor.RecordValues;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.NoopTypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriterImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.KeyGeneratorControls;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.streamprocessor.state.MutableLastProcessedPositionState;
import java.util.function.BooleanSupplier;

public final class StreamProcessorContext implements ReadonlyStreamProcessorContext {

  private static final StreamProcessorListener NOOP_LISTENER = processedCommand -> {};

  private final TypedStreamWriterProxy streamWriterProxy = new TypedStreamWriterProxy();
  private final NoopTypedStreamWriter noopTypedStreamWriter = new NoopTypedStreamWriter();

  private ActorControl actor;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private TypedStreamWriter logStreamWriter = noopTypedStreamWriter;
  private TypedResponseWriterImpl typedResponseWriter;

  private RecordValues recordValues;
  private ZeebeDbState zeebeState;
  private TransactionContext transactionContext;
  private EventApplier eventApplier;

  private BooleanSupplier abortCondition;
  private StreamProcessorListener streamProcessorListener = NOOP_LISTENER;

  private int maxFragmentSize;
  private StreamProcessorMode streamProcessorMode = StreamProcessorMode.PROCESSING;
  private ProcessingScheduleService processingScheduleService;
  private MutableLastProcessedPositionState lastProcessedPositionState;
  private Writers writers;
  private LogStreamBatchWriter logStreamBatchWriter;
  private CommandResponseWriter commandResponseWriter;

  public StreamProcessorContext() {
    streamWriterProxy.wrap(logStreamWriter);
  }

  public StreamProcessorContext actor(final ActorControl actor) {
    this.actor = actor;
    processingScheduleService = new ProcessingScheduleServiceImpl(actor);
    return this;
  }

  @Override
  public ProcessingScheduleService getScheduleService() {
    return processingScheduleService;
  }

  @Override
  public LogStream getLogStream() {
    return logStream;
  }

  @Override
  public TypedStreamWriter getLogStreamWriter() {
    return streamWriterProxy;
  }

  @Override
  public Writers getWriters() {
    return writers;
  }

  @Override
  public MutableZeebeState getZeebeState() {
    return zeebeState;
  }

  @Override
  public int getPartitionId() {
    return getLogStream().getPartitionId();
  }

  public MutableLastProcessedPositionState getLastProcessedPositionState() {
    return lastProcessedPositionState;
  }

  public StreamProcessorContext listener(final StreamProcessorListener streamProcessorListener) {
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

  public StreamProcessorContext zeebeState(final ZeebeDbState zeebeState) {
    this.zeebeState = zeebeState;
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

  public StreamProcessorContext logStreamWriter(final TypedStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
    return this;
  }

  public StreamProcessorContext commandResponseWriter(
      final CommandResponseWriter commandResponseWriter) {
    this.commandResponseWriter = commandResponseWriter;
    typedResponseWriter =
        new TypedResponseWriterImpl(commandResponseWriter, getLogStream().getPartitionId());
    return this;
  }

  public TypedResponseWriterImpl getTypedResponseWriter() {
    return typedResponseWriter;
  }

  public StreamProcessorContext maxFragmentSize(final int maxFragmentSize) {
    this.maxFragmentSize = maxFragmentSize;
    return this;
  }

  public StreamProcessorContext eventApplier(final EventApplier eventApplier) {
    this.eventApplier = eventApplier;
    return this;
  }

  public StreamProcessorContext processorMode(final StreamProcessorMode streamProcessorMode) {
    this.streamProcessorMode = streamProcessorMode;
    return this;
  }

  public KeyGeneratorControls getKeyGeneratorControls() {
    return zeebeState.getKeyGeneratorControls();
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

  public void enableLogStreamWriter() {
    streamWriterProxy.wrap(logStreamWriter);
  }

  public void disableLogStreamWriter() {
    streamWriterProxy.wrap(noopTypedStreamWriter);
  }

  public StreamProcessorMode getProcessorMode() {
    return streamProcessorMode;
  }

  public void writers(final Writers writers) {
    this.writers = writers;
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
}

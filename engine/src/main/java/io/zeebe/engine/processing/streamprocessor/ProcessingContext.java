/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.db.DbContext;
import io.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.NoopTypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.util.sched.ActorControl;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class ProcessingContext implements ReadonlyProcessingContext {

  private ActorControl actor;
  private EventFilter eventFilter;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private TypedStreamWriter logStreamWriter = new NoopTypedStreamWriter();
  private CommandResponseWriter commandResponseWriter;

  private RecordValues recordValues;
  private RecordProcessorMap recordProcessorMap;
  private ZeebeState zeebeState;
  private DbContext dbContext;

  private BooleanSupplier abortCondition;
  private Consumer<TypedRecord> onProcessedListener = record -> {};
  private int maxFragmentSize;
  private boolean detectReprocessingInconsistency;

  public ProcessingContext actor(final ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public ProcessingContext eventFilter(final EventFilter eventFilter) {
    this.eventFilter = eventFilter;
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

  public ProcessingContext recordProcessorMap(final RecordProcessorMap recordProcessorMap) {
    this.recordProcessorMap = recordProcessorMap;
    return this;
  }

  public ProcessingContext zeebeState(final ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
    return this;
  }

  public ProcessingContext dbContext(final DbContext dbContext) {
    this.dbContext = dbContext;
    return this;
  }

  public ProcessingContext abortCondition(final BooleanSupplier abortCondition) {
    this.abortCondition = abortCondition;
    return this;
  }

  public ProcessingContext logStreamWriter(final TypedStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
    return this;
  }

  public ProcessingContext commandResponseWriter(
      final CommandResponseWriter commandResponseWriter) {
    this.commandResponseWriter = commandResponseWriter;
    return this;
  }

  public ProcessingContext onProcessedListener(final Consumer<TypedRecord> onProcessedListener) {
    this.onProcessedListener = onProcessedListener;
    return this;
  }

  public ProcessingContext maxFragmentSize(final int maxFragmentSize) {
    this.maxFragmentSize = maxFragmentSize;
    return this;
  }

  @Override
  public ActorControl getActor() {
    return actor;
  }

  @Override
  public EventFilter getEventFilter() {
    return eventFilter;
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
  public int getMaxFragmentSize() {
    return maxFragmentSize;
  }

  @Override
  public TypedStreamWriter getLogStreamWriter() {
    return logStreamWriter;
  }

  @Override
  public RecordValues getRecordValues() {
    return recordValues;
  }

  @Override
  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  @Override
  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  @Override
  public DbContext getDbContext() {
    return dbContext;
  }

  @Override
  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriter;
  }

  @Override
  public BooleanSupplier getAbortCondition() {
    return abortCondition;
  }

  public Consumer<TypedRecord> getOnProcessedListener() {
    return onProcessedListener;
  }

  public boolean isDetectReprocessingInconsistency() {
    return detectReprocessingInconsistency;
  }

  public ProcessingContext setDetectReprocessingInconsistency(
      final boolean detectReprocessingInconsistency) {
    this.detectReprocessingInconsistency = detectReprocessingInconsistency;
    return this;
  }
}

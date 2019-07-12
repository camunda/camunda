/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import io.zeebe.db.DbContext;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.util.sched.ActorControl;
import java.util.Map;
import java.util.function.BooleanSupplier;

public class ProcessingContext implements ReadonlyProcessingContext {

  private ActorControl actor;
  private EventFilter eventFilter;
  private LogStream logStream;
  private LogStreamReader logStreamReader;
  private TypedStreamWriter logStreamWriter;
  private CommandResponseWriter commandResponseWriter;

  private Map<ValueType, UnifiedRecordValue> eventCache;
  private RecordProcessorMap recordProcessorMap;
  private ZeebeState zeebeState;
  private DbContext dbContext;

  private BooleanSupplier abortCondition;

  public ProcessingContext actor(ActorControl actor) {
    this.actor = actor;
    return this;
  }

  public ProcessingContext eventFilter(EventFilter eventFilter) {
    this.eventFilter = eventFilter;
    return this;
  }

  public ProcessingContext logStream(LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ProcessingContext logStreamReader(LogStreamReader logStreamReader) {
    this.logStreamReader = logStreamReader;
    return this;
  }

  public ProcessingContext eventCache(Map<ValueType, UnifiedRecordValue> eventCache) {
    this.eventCache = eventCache;
    return this;
  }

  public ProcessingContext recordProcessorMap(RecordProcessorMap recordProcessorMap) {
    this.recordProcessorMap = recordProcessorMap;
    return this;
  }

  public ProcessingContext zeebeState(ZeebeState zeebeState) {
    this.zeebeState = zeebeState;
    return this;
  }

  public ProcessingContext dbContext(DbContext dbContext) {
    this.dbContext = dbContext;
    return this;
  }

  public ProcessingContext abortCondition(BooleanSupplier abortCondition) {
    this.abortCondition = abortCondition;
    return this;
  }

  public ProcessingContext logStreamWriter(TypedStreamWriter logStreamWriter) {
    this.logStreamWriter = logStreamWriter;
    return this;
  }

  public ProcessingContext commandResponseWriter(CommandResponseWriter commandResponseWriter) {
    this.commandResponseWriter = commandResponseWriter;
    return this;
  }

  public ActorControl getActor() {
    return actor;
  }

  public EventFilter getEventFilter() {
    return eventFilter;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public LogStreamReader getLogStreamReader() {
    return logStreamReader;
  }

  public TypedStreamWriter getLogStreamWriter() {
    return logStreamWriter;
  }

  public Map<ValueType, UnifiedRecordValue> getEventCache() {
    return eventCache;
  }

  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public DbContext getDbContext() {
    return dbContext;
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return commandResponseWriter;
  }

  public BooleanSupplier getAbortCondition() {
    return abortCondition;
  }
}

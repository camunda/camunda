/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.streamprocessor;

import io.zeebe.db.TransactionContext;
import io.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.EventApplier;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.util.sched.ActorControl;
import java.util.function.BooleanSupplier;

public interface ReadonlyProcessingContext {

  /** @return the actor on which the processing runs */
  ActorControl getActor();

  /** @return the logstream, on which the processor runs */
  LogStream getLogStream();

  /** @return the reader, which is used by the processor to read next events */
  LogStreamReader getLogStreamReader();

  /**
   * @return the maximum fragment size we can write and read this contains the record metadata and
   *     record value etc.
   */
  int getMaxFragmentSize();

  /** @return the writer, which is used by the processor to write follow up events */
  TypedStreamWriter getLogStreamWriter();

  /** @return the pool, which contains the mapping from ValueType to UnpackedObject (record) */
  RecordValues getRecordValues();

  /** @return the map of processors, which are executed during processing */
  RecordProcessorMap getRecordProcessorMap();

  /** @return the state, where the data is stored during processing */
  ZeebeState getZeebeState();

  /** @return the transaction context for the current actor */
  TransactionContext getTransactionContext();

  /** @return the response writer, which is used during processing */
  CommandResponseWriter getCommandResponseWriter();

  /** @return condition which indicates, whether the processing should stop or not */
  BooleanSupplier getAbortCondition();

  /** @return the consumer of events to apply their state changes */
  EventApplier getEventApplier();

  /** @return the writer of events that also changes state for each event it writes */
  StateWriter getStateWriter();

  /** @return the writer, which is used by the processors to write (follow-up) commands */
  TypedCommandWriter getCommandWriter();

  /** @return the writer, which is used by the processors to write command rejections */
  TypedRejectionWriter getRejectionWriter();
}

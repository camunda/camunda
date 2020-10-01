/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.engine.util.Records.workflowInstance;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.TypedRecordProcessorFactory;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.function.Consumer;

public class StreamProcessingComposite {

  private static final String STREAM_NAME = "stream-";

  private final TestStreams streams;
  private final int partitionId;
  private final ZeebeDbFactory zeebeDbFactory;
  private ZeebeState zeebeState;

  public StreamProcessingComposite(
      final TestStreams streams, final int partitionId, final ZeebeDbFactory zeebeDbFactory) {
    this.streams = streams;
    this.partitionId = partitionId;
    this.zeebeDbFactory = zeebeDbFactory;
  }

  public LogStreamRecordWriter getLogStreamRecordWriter(final int partitionId) {
    final String logName = getLogName(partitionId);
    return streams.getLogStreamRecordWriter(logName);
  }

  public StreamProcessor startTypedStreamProcessor(final StreamProcessorTestFactory factory) {
    return startTypedStreamProcessor(factory, r -> {});
  }

  public StreamProcessor startTypedStreamProcessor(
      final StreamProcessorTestFactory factory, final Consumer<TypedRecord> onProcessedListener) {
    return startTypedStreamProcessor(
        (processingContext) -> {
          zeebeState = processingContext.getZeebeState();
          processingContext.onProcessedListener(onProcessedListener);
          return factory.build(
              TypedRecordProcessors.processors(zeebeState.getKeyGenerator()), processingContext);
        });
  }

  public StreamProcessor startTypedStreamProcessor(final TypedRecordProcessorFactory factory) {
    return startTypedStreamProcessor(partitionId, factory);
  }

  public StreamProcessor startTypedStreamProcessor(
      final int partitionId, final TypedRecordProcessorFactory factory) {
    return streams.startStreamProcessor(
        getLogName(partitionId),
        zeebeDbFactory,
        (processingContext -> {
          zeebeState = processingContext.getZeebeState();
          return factory.createProcessors(processingContext);
        }));
  }

  public void closeStreamProcessor(final int partitionId) {
    try {
      streams.closeProcessor(getLogName(partitionId));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public StreamProcessor getStreamProcessor(final int partitionId) {
    return streams.getStreamProcessor(getLogName(partitionId));
  }

  public ZeebeState getZeebeState() {
    return zeebeState;
  }

  public RecordStream events() {
    return new RecordStream(streams.events(getLogName(partitionId)));
  }

  public long writeWorkflowInstanceEvent(final WorkflowInstanceIntent intent) {
    return writeWorkflowInstanceEvent(intent, 1);
  }

  public long writeWorkflowInstanceEventWithSource(
      final WorkflowInstanceIntent intent, final int instanceKey, final long sourceEventPosition) {
    return streams
        .newRecord(getLogName(partitionId))
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .sourceRecordPosition(sourceEventPosition)
        .intent(intent)
        .write();
  }

  public long writeWorkflowInstanceEvent(
      final WorkflowInstanceIntent intent, final int instanceKey) {
    return streams
        .newRecord(getLogName(partitionId))
        .event(workflowInstance(instanceKey))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .write();
  }

  public long writeEvent(final long key, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.EVENT)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeEvent(final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.EVENT)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeBatch(final RecordToWrite... recordToWrites) {
    return streams.writeBatch(getLogName(partitionId), recordToWrites);
  }

  public long writeCommandOnPartition(
      final int partition, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partition))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommandOnPartition(
      final int partition, final long key, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partition))
        .key(key)
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(final long key, final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.COMMAND)
        .key(key)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(final Intent intent, final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.COMMAND)
        .intent(intent)
        .event(value)
        .write();
  }

  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnpackedObject value) {
    return streams
        .newRecord(getLogName(partitionId))
        .recordType(RecordType.COMMAND)
        .requestId(requestId)
        .requestStreamId(requestStreamId)
        .intent(intent)
        .event(value)
        .write();
  }

  public static String getLogName(final int partitionId) {
    return STREAM_NAME + partitionId;
  }

  @FunctionalInterface
  public interface StreamProcessorTestFactory {
    TypedRecordProcessors build(
        TypedRecordProcessors builder, ReadonlyProcessingContext processingContext);
  }
}

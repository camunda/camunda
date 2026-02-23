/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.TestStreams.FluentLogWriter;
import io.camunda.zeebe.engine.util.client.CommandWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.logstreams.util.TestLogStream;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class StreamProcessingComposite implements CommandWriter {

  private static final String STREAM_NAME = "stream-";

  private final TestStreams streams;
  private final int partitionId;
  private final ZeebeDbFactory<?> zeebeDbFactory;
  private MutableProcessingState processingState;
  private final WriteActor writeActor = new WriteActor();

  public StreamProcessingComposite(
      final TestStreams streams,
      final int partitionId,
      final ZeebeDbFactory<?> zeebeDbFactory,
      final ActorScheduler actorScheduler) {
    this.streams = streams;
    this.partitionId = partitionId;
    this.zeebeDbFactory = zeebeDbFactory;
    actorScheduler.submitActor(writeActor).join();
  }

  public TestLogStream getLogStream(final int partitionId) {
    return streams.getLogStream(getLogName(partitionId));
  }

  public LogStreamWriter newLogStreamWriter(final int partitionId) {
    final String logName = getLogName(partitionId);
    return streams.newLogStreamWriter(logName);
  }

  public StreamProcessor startTypedStreamProcessor(
      final StreamProcessorTestFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    return startTypedStreamProcessor(
        (processingContext) -> createTypedRecordProcessors(factory, processingContext),
        streamProcessorListenerOpt);
  }

  private TypedRecordProcessors createTypedRecordProcessors(
      final StreamProcessorTestFactory factory,
      final TypedRecordProcessorContext typedRecordProcessorContext) {
    processingState = typedRecordProcessorContext.getProcessingState();

    return factory.build(TypedRecordProcessors.processors(), typedRecordProcessorContext);
  }

  public StreamProcessor startTypedStreamProcessor(
      final TypedRecordProcessorFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt) {
    return startTypedStreamProcessor(
        partitionId, factory, streamProcessorListenerOpt, cfg -> {}, true);
  }

  public StreamProcessor startTypedStreamProcessor(
      final int partitionId,
      final TypedRecordProcessorFactory factory,
      final Optional<StreamProcessorListener> streamProcessorListenerOpt,
      final Consumer<StreamProcessorBuilder> processorConfiguration,
      final boolean awaitOpening) {
    final var result =
        streams.startStreamProcessor(
            getLogName(partitionId),
            zeebeDbFactory,
            (processingContext -> {
              processingState = processingContext.getProcessingState();

              return factory.createProcessors(processingContext);
            }),
            streamProcessorListenerOpt,
            processorConfiguration,
            awaitOpening);

    return result;
  }

  public void pauseProcessing(final int partitionId) {
    streams.pauseProcessing(getLogName(partitionId));
  }

  public void banInstanceInNewTransaction(final int partitionId, final long processInstanceKey) {
    streams.banInstanceInNewTransaction(getLogName(partitionId), processInstanceKey);

    final var errorRecord = new ErrorRecord();
    errorRecord.initErrorRecord(new Exception("Instance was banned from outside."), -1);
    errorRecord.setProcessInstanceKey(processInstanceKey);

    writeBatch(
        partitionId,
        RecordToWrite.event().key(processInstanceKey).error(ErrorIntent.CREATED, errorRecord));
  }

  public void resumeProcessing(final int partitionId) {
    streams.resumeProcessing(getLogName(partitionId));
  }

  public void snapshot(final int partitionId) {
    streams.snapshot(getLogName(partitionId));
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

  public StreamClock getStreamClock(final int partitionId) {
    return streams.getStreamClock(getLogName(partitionId));
  }

  public MeterRegistry getMeterRegistry(final int partitionId) {
    return streams.getMeterRegistry(getLogName(partitionId));
  }

  public MutableProcessingState getProcessingState() {
    return processingState;
  }

  public MutableProcessingState getProcessingState(final String streamName) {
    return streams.getProcessingState(streamName);
  }

  public RecordStream events() {
    return new RecordStream(streams.events(getLogName(partitionId)));
  }

  public long writeBatch(final RecordToWrite... recordsToWrite) {
    return writeBatch(partitionId, recordsToWrite);
  }

  public long writeBatch(final int partitionId, final RecordToWrite... recordsToWrite) {
    final var writer = streams.newLogStreamWriter(getLogName(partitionId));
    return writeActor
        .submit(() -> writer.tryWrite(WriteContext.internal(), Arrays.asList(recordsToWrite)).get())
        .join();
  }

  @Override
  public long writeCommand(final Intent intent, final UnifiedRecordValue value) {
    return writeCommand(intent, value, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Override
  public long writeCommand(
      final Intent intent, final UnifiedRecordValue value, final String... authorizedTenants) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .authorizations(authorizedTenants)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final Intent intent,
      final String username,
      final UnifiedRecordValue value,
      final String... authorizedTenants) {
    final var requestId = new Random().nextLong();
    final var requestStreamId = new Random().nextInt();
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .authorizationsWithUsername(username, authorizedTenants)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(final long key, final Intent intent, final UnifiedRecordValue value) {
    return writeCommand(key, intent, value, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Override
  public long writeCommand(
      final Intent intent, final UnifiedRecordValue value, final AuthInfo authorizations) {
    final var requestId = new Random().nextLong();
    final var requestStreamId = new Random().nextInt();
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .authorizations(authorizations)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final long key,
      final Intent intent,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .key(key)
            .intent(intent)
            .authorizations(authorizedTenants)
            .event(recordValue);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final long key,
      final Intent intent,
      final String username,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants) {
    final var requestId = new Random().nextLong();
    final var requestStreamId = new Random().nextInt();
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .key(key)
            .intent(intent)
            .authorizationsWithUsername(username, authorizedTenants)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .event(recordValue);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final long key,
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .key(key)
            .requestStreamId(requestStreamId)
            .requestId(requestId)
            .intent(intent)
            .authorizations(authorizedTenants)
            .event(recordValue);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .requestStreamId(requestStreamId)
            .requestId(requestId)
            .intent(intent)
            .authorizations(authorizedTenants)
            .event(recordValue);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final long key,
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final String username,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .key(key)
            .requestStreamId(requestStreamId)
            .requestId(requestId)
            .intent(intent)
            .authorizationsWithUsername(username, authorizedTenants)
            .event(recordValue);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue value) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .intent(intent)
            .authorizations(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue value,
      final String username) {
    final var writer =
        streams
            .newRecord(getLogName(partitionId))
            .recordType(RecordType.COMMAND)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .intent(intent)
            .authorizationsWithUsername(username, TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommandOnPartition(
      final int partitionId, final UnaryOperator<FluentLogWriter> builder) {
    final var writer =
        builder.apply(streams.newRecord(getLogName(partitionId)).recordType(RecordType.COMMAND));
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommandOnPartition(
      final int partition, final Intent intent, final UnifiedRecordValue value) {

    final var writer =
        streams
            .newRecord(getLogName(partition))
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .authorizations(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommandOnPartition(
      final int partition,
      final Intent intent,
      final UnifiedRecordValue value,
      final String username) {
    final var requestId = new Random().nextLong();
    final var requestStreamId = new Random().nextInt();
    final var writer =
        streams
            .newRecord(getLogName(partition))
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .usernameAuthorizations(username)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommandOnPartition(
      final int partition, final long key, final Intent intent, final UnifiedRecordValue value) {
    return writeCommandOnPartition(
        partition, key, intent, value, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Override
  public long writeCommandOnPartition(
      final int partition,
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final AuthInfo authorizations) {
    final var writer =
        streams
            .newRecord(getLogName(partition))
            .key(key)
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .authorizations(authorizations)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  @Override
  public long writeCommandOnPartition(
      final int partition,
      final long key,
      final Intent intent,
      final UnifiedRecordValue value,
      final String... authorizedTenants) {
    return writeCommandOnPartition(
        partition, key, intent, value, AuthorizationUtil.getAuthInfo(authorizedTenants));
  }

  @Override
  public long writeCommandOnPartition(
      final int partition,
      final long key,
      final Intent intent,
      final String username,
      final UnifiedRecordValue value,
      final String... authorizedTenants) {
    final var requestId = new Random().nextLong();
    final var requestStreamId = new Random().nextInt();
    final var writer =
        streams
            .newRecord(getLogName(partition))
            .key(key)
            .recordType(RecordType.COMMAND)
            .intent(intent)
            .authorizationsWithUsername(username, authorizedTenants)
            .requestId(requestId)
            .requestStreamId(requestStreamId)
            .event(value);
    return writeActor.submit(writer::write).join();
  }

  public static String getLogName(final int partitionId) {
    return STREAM_NAME + partitionId;
  }

  /** Used to run writes within an actor thread. */
  private static final class WriteActor extends Actor {
    public ActorFuture<Long> submit(final Callable<Long> write) {
      return actor.call(write);
    }
  }

  @FunctionalInterface
  public interface StreamProcessorTestFactory {
    TypedRecordProcessors build(
        TypedRecordProcessors builder, TypedRecordProcessorContext typedRecordProcessorContext);
  }
}

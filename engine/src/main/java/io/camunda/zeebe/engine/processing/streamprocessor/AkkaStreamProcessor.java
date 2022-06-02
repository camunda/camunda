/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.BehaviorBuilder;
import akka.actor.typed.javadsl.Behaviors;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchWriter;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import java.util.function.Function;

public class AkkaStreamProcessor {

  final Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private final LogStream logStream;
  // snapshotting
  private final ZeebeDb zeebeDb;
  // processing
  private final ProcessingContext processingContext;
  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private LogStreamReader logStreamReader;
  private final Function<LogStreamBatchWriter, TypedStreamWriter> typedStreamWriterFactory;
  private final AkkaCompatActor compat;
  private final int partitionId;
  private ActorContext<Command> ctx;
  private ReplayStateMachine replayStateMachine;
  private LogStreamBatchWriter logStreamWriter;
  private AkkaStreamReplaying akkaStreamReplaying;

  public AkkaStreamProcessor(
      final AkkaCompatActor compat, final StreamProcessorBuilder processorBuilder) {
    this.compat = compat;

    typedRecordProcessorFactory = processorBuilder.getTypedRecordProcessorFactory();
    typedStreamWriterFactory = processorBuilder.getTypedStreamWriterFactory();
    zeebeDb = processorBuilder.getZeebeDb();
    eventApplierFactory = processorBuilder.getEventApplierFactory();

    processingContext = processorBuilder.getProcessingContext().eventCache(new RecordValues());
    logStream = processingContext.getLogStream();
    partitionId = logStream.getPartitionId();
  }

  private Behavior<Command> create() {
    return Behaviors.setup(
        ctx -> {
          this.ctx = ctx;
          compat.onActor(
              ctx, logStream::newLogStreamReader, GotLogStreamReader::new, LogStreamFailure::new);
          return Behaviors.receive(Command.class)
              .onMessage(
                  GotLogStreamReader.class,
                  (reader) -> {
                    logStreamReader = reader.reader;
                    initForReplay();
                    return replay();
                  })
              .build();
        });
  }

  private void initForReplay() {
    final var snapshotPosition = recoverFromSnapshot();
    initProcessors();
    akkaStreamReplaying = new AkkaStreamReplaying();
    replayStateMachine = new ReplayStateMachine(processingContext, () -> true);
  }

  private long recoverFromSnapshot() {
    final var zeebeState = recoverState();

    final long snapshotPosition =
        zeebeState.getLastProcessedPositionState().getLastSuccessfulProcessedRecordPosition();

    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader
        && processingContext.getProcessorMode() == StreamProcessorMode.PROCESSING) {
      throw new IllegalStateException(
          String.format(
              "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.",
              snapshotPosition, "AkkaStreamProcessor"));
    }
    ctx.getLog()
        .info(
            "Recovered state of partition {} from snapshot at position {}",
            partitionId,
            snapshotPosition);
    return snapshotPosition;
  }

  private ZeebeDbState recoverState() {
    final TransactionContext transactionContext = zeebeDb.createContext();
    final ZeebeDbState zeebeState = new ZeebeDbState(partitionId, zeebeDb, transactionContext);

    processingContext.transactionContext(transactionContext);
    processingContext.zeebeState(zeebeState);
    processingContext.eventApplier(eventApplierFactory.apply(zeebeState));

    return zeebeState;
  }

  private void initProcessors() {
    final TypedRecordProcessors typedRecordProcessors =
        typedRecordProcessorFactory.createProcessors(processingContext);

    final RecordProcessorMap recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();

    processingContext.recordProcessorMap(recordProcessorMap);
  }

  private Behavior<Command> replay() {
    return akkaStreamReplaying.startReplay();
  }

  private Behavior<Command> processing() {
    return common().build();
  }

  BehaviorBuilder<Command> common() {
    return Behaviors.receive(Command.class);
  }

  public Behavior<Command> onReplayCompleted() {
    compat.onActor(
        ctx,
        logStream::newLogStreamBatchWriter,
        GotLogStreamBatchWriter::new,
        LogStreamFailure::new);

    return Behaviors.receive(Command.class)
        .onMessage(
            GotLogStreamBatchWriter.class,
            (msg) -> {
              logStreamWriter = msg.writer;
              return processing();
            })
        .build();
  }

  public record RecordAvailable() implements Command {}

  public record GetPositions(ActorRef<StreamProcessorResponse> replyTo) implements Command {}

  public record Positions(long lastWritten, long lastProcessed)
      implements StreamProcessorResponse {}

  record GotLogStreamReader(LogStreamReader reader) implements Command {}

  record GotLogStreamBatchWriter(LogStreamBatchWriter writer) implements Command {}

  record LogStreamFailure(Throwable cause) implements Command {}

  interface Command {}

  interface StreamProcessorResponse {}
}

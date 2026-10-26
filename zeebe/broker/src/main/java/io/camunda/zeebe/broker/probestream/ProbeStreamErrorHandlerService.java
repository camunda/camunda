/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.probestream;

import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.stream.probe.ActivatedProbe;
import io.camunda.zeebe.protocol.record.intent.ProbeIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.NoopScheduledCommandCache;
import io.camunda.zeebe.stream.impl.BufferedTaskResultBuilder;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.Objects;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Yields a probe that could not be pushed on any connection of its stream, so the engine can
 * re-dispatch it once, as {@code YieldingJobStreamErrorHandler} yields a job. The YIELD command is
 * written on the probe's partition, without the input, which carries the secret values.
 */
public final class ProbeStreamErrorHandlerService extends Actor
    implements PartitionListener, RemoteStreamErrorHandler<ActivatedProbe> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProbeStreamErrorHandlerService.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(1));

  private final Int2ObjectHashMap<LogStreamWriter> partitionWriters = new Int2ObjectHashMap<>();

  public ProbeStreamErrorHandlerService() {
    super("ProbeStreamErrorHandler");
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return actor.call(
        () -> {
          partitionWriters.remove(partitionId);
        });
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId,
      final long term,
      final LogStream logStream,
      final QueryService queryService) {
    final var writer =
        Objects.requireNonNull(logStream, "must specify a log stream").newLogStreamWriter();
    return actor.call(
        () -> {
          partitionWriters.put(partitionId, writer);
        });
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return actor.call(
        () -> {
          partitionWriters.remove(partitionId);
        });
  }

  @Override
  public void handleError(final Throwable error, final ActivatedProbe probe) {
    actor.run(() -> yieldProbe(error, probe));
  }

  private void yieldProbe(final Throwable error, final ActivatedProbe probe) {
    final long probeKey = probe.probeKey();
    final var partitionId = Protocol.decodePartitionId(probeKey);
    final var writer = partitionWriters.get(partitionId);
    if (writer == null) {
      THROTTLED_LOGGER.warn(
          "Cannot yield probe {} after a failed push: no writer for partition {}",
          probeKey,
          partitionId);
      return;
    }

    LOGGER.trace("Failed to push probe {}. Yielding...", probeKey, error);
    final var resultBuilder =
        new BufferedTaskResultBuilder(writer::canWriteEvents, new NoopScheduledCommandCache());
    final var yielded = probe.probeRecord().copy().resetInput().resetSecretReferences();
    resultBuilder.appendCommandRecord(probeKey, ProbeIntent.YIELD, yielded);
    final var written =
        writer.tryWrite(WriteContext.internal(), resultBuilder.build().getRecordBatch().entries());
    if (written.isLeft()) {
      THROTTLED_LOGGER.warn(
          "Failed to yield probe {} on partition {}; it times out at its deadline: {}",
          probeKey,
          partitionId,
          written.getLeft());
    }
  }
}

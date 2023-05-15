/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.engine.processing.streamprocessor.ActivatedJob;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.impl.BufferedTaskResultBuilder;
import io.camunda.zeebe.transport.stream.api.RemoteStreamErrorHandler;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;

/**
 * A {@link RemoteStreamErrorHandler} for {@link ActivatedJob} payloads, which will write any
 * followup commands produced by the given {@link JobStreamErrorHandler} delegate. The followup
 * commands are written on the same partition as the job's.
 *
 * <p>In order to obtain partition writers, this implementation is then also a {@link
 * PartitionListener} which must be added to the {@link
 * BrokerStartupContext#getPartitionListeners()} during the broker's startup process.
 *
 * <p>It's possible that a job was pushed when this node was leader for its partition, but that the
 * error handling occurs after an election change, at which point the job will remain activated
 * until it times out.
 */
final class RemoteJobStreamErrorHandler implements RemoteStreamErrorHandler<ActivatedJob> {
  private static final Logger LOGGER = Loggers.JOB_STREAM;
  private static final Logger NO_WRITER_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(1));
  private static final Logger NO_ACTION_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(1));
  private static final Logger FAILED_WRITER_LOGGER =
      new ThrottledLogger(LOGGER, Duration.ofSeconds(1));

  private final JobStreamErrorHandler errorHandler;

  private final Int2ObjectHashMap<LogStreamWriter> partitionWriters = new Int2ObjectHashMap<>();

  RemoteJobStreamErrorHandler(final JobStreamErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  @Override
  public void handleError(final Throwable error, final ActivatedJob job) {
    final var partitionId = Protocol.decodePartitionId(job.jobKey());
    final var writer = partitionWriters.get(partitionId);
    if (writer == null) {
      NO_WRITER_LOGGER.warn(
          """
          Cannot handle failed job push on partition {} there is no writer registered;
          this can occur during an election""",
          partitionId);
      return;
    }

    final var resultBuilder = new BufferedTaskResultBuilder(writer::canWriteEvents);
    errorHandler.handleError(job, error, resultBuilder);

    final var result = resultBuilder.build();
    writeEntries(partitionId, job, writer, result);
  }

  void addWriter(final int partitionId, final LogStreamWriter writer) {
    partitionWriters.put(partitionId, writer);
  }

  void removeWriter(final int partitionId) {
    partitionWriters.remove(partitionId);
  }

  private void writeEntries(
      final int partitionId,
      final ActivatedJob job,
      final LogStreamWriter writer,
      final TaskResult result) {
    final var position = writer.tryWrite(result.getRecordBatch().entries());
    if (position == 0) {
      NO_ACTION_LOGGER.warn(
          """
           Failed to push job {} on partition {}, but the error handler did not return anything;
           the job will be activated again when it times out""",
          job.jobKey(),
          partitionId);
    } else if (position < 0) {
      FAILED_WRITER_LOGGER.warn(
          """
          Failed to handle failed job push {} on partition {};
          job will remain activated until it times out""",
          job.jobKey(),
          partitionId);
    }
  }
}

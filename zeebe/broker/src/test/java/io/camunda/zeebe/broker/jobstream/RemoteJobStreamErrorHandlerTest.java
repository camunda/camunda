/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.jobstream;

import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.stream.job.ActivatedJob;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.agrona.MutableDirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

final class RemoteJobStreamErrorHandlerTest {

  private final TestConcurrencyControl executor = new TestConcurrencyControl();
  private final TestErrorHandler jobErrorHandler = new TestErrorHandler();

  @Test
  void shouldNotCallDelegateHandlerIfNoWriter() {
    // given
    final var handler = new RemoteJobStreamErrorHandler(jobErrorHandler);
    final var job = new TestActivatedJob(1, new JobRecord());

    // when
    handler.handleError(new RuntimeException("Failure"), job);

    // then
    Assertions.assertThat(jobErrorHandler.errors()).isEmpty();
  }

  @Test
  void shouldDelegateToJobHandler() {
    // given
    final var handler = new RemoteJobStreamErrorHandler(jobErrorHandler);
    final var job = new TestActivatedJob(Protocol.encodePartitionId(1, 1), new JobRecord());
    final var error = new RuntimeException("Failure");
    handler.addWriter(1, (context, entries, ignored) -> Either.right(1L));

    // when
    handler.handleError(error, job);

    // then
    Assertions.assertThat(jobErrorHandler.errors())
        .hasSize(1)
        .first()
        .extracting(TestErrorHandler.Error::error, TestErrorHandler.Error::job)
        .containsExactly(error, job);
  }

  @Test
  void shouldRemoveWriter() {
    // given
    final var handler = new RemoteJobStreamErrorHandler(jobErrorHandler);
    final var job = new TestActivatedJob(1, new JobRecord());
    handler.addWriter(1, (context, entries, ignored) -> Either.right(1L));

    // when
    handler.removeWriter(1);
    handler.handleError(new RuntimeException("Failure"), job);

    // then
    Assertions.assertThat(jobErrorHandler.errors()).isEmpty();
  }

  @Test
  void shouldWriteResultingEntries() {
    // given
    final var handler = new RemoteJobStreamErrorHandler(jobErrorHandler);
    final var job = new TestActivatedJob(Protocol.encodePartitionId(1, 1), new JobRecord());
    final var writtenEntries = new ArrayList<LogAppendEntry>();
    handler.addWriter(
        1,
        (context, entries, ignored) -> {
          writtenEntries.addAll(entries);
          return Either.right(1L);
        });

    // when
    handler.handleError(new RuntimeException("failure"), job);

    // then
    Assertions.assertThat(jobErrorHandler.errors()).hasSize(1);
    Assertions.assertThat(writtenEntries)
        .hasSize(1)
        .first()
        .extracting(LogAppendEntry::key, LogAppendEntry::recordValue)
        .containsExactly(1L, job.jobRecord());
  }

  // TODO: use actual one if possible
  private record TestActivatedJob(long jobKey, JobRecord jobRecord) implements ActivatedJob {

    @Override
    public int getLength() {
      return 0;
    }

    @Override
    public int write(final MutableDirectBuffer buffer, final int offset) {
      return 0;
    }
  }

  private record TestErrorHandler(List<TestErrorHandler.Error> errors)
      implements JobStreamErrorHandler {

    private TestErrorHandler() {
      this(new ArrayList<>());
    }

    @Override
    public void handleError(
        final ActivatedJob job, final Throwable error, final TaskResultBuilder resultBuilder) {
      resultBuilder.appendCommandRecord(1, JobIntent.FAIL, job.jobRecord());
      errors.add(new TestErrorHandler.Error(job, error));
    }

    private record Error(ActivatedJob job, Throwable error) {}
  }
}

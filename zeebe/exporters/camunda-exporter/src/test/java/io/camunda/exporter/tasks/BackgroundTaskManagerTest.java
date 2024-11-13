/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.exporter.config.ExporterConfiguration.ArchiverConfiguration;
import io.camunda.exporter.tasks.archiver.ArchiverRepository.NoopArchiverRepository;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.agrona.collections.MutableInteger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

@AutoCloseResources
final class BackgroundTaskManagerTest {
  @AutoCloseResource(closeMethod = "shutdownNow")
  private final ScheduledThreadPoolExecutor executor =
      Mockito.spy(new ScheduledThreadPoolExecutor(1));

  @Nested
  final class StartTest {
    private final BackgroundTaskManager archiver =
        new BackgroundTaskManager(
            1,
            new NoopArchiverRepository(),
            LoggerFactory.getLogger(BackgroundTaskManagerTest.class),
            executor,
            // return unfinished futures to have a deterministic count of submitted tasks
            List.of(CompletableFuture::new, CompletableFuture::new),
            new ArchiverConfiguration());

    @Test
    void shouldNotResubmitTasksOnStart() {
      // given
      archiver.start();

      // when
      archiver.start();

      // then - we can't use `getTaskCount()` because that's an approximation of the number of tasks
      // and it may be wrong at times, as per the docs
      Mockito.verify(executor, Mockito.times(2)).submit(Mockito.any(Runnable.class));
    }

    @Test
    void shouldResubmitUnsubmittedTasksOnStart() {
      // given
      final var count = new MutableInteger();
      Mockito.doAnswer(
              inv -> {
                final var invocation = count.getAndIncrement();
                // fail on the second call
                if (invocation == 1) {
                  throw new RuntimeException("fail");
                }

                return inv.callRealMethod();
              })
          .when(executor)
          .submit(Mockito.any(Runnable.class));
      assertThatCode(archiver::start)
          .as("throws on the second task submission")
          .isInstanceOf(RuntimeException.class);
      Mockito.verify(executor, Mockito.times(2)).submit(Mockito.any(Runnable.class));

      // when
      archiver.start();

      // we can't use `getTaskCount()` because that's an approximation of the number of tasks
      // and it may be wrong at times, as per the docs
      // then - we actually expect 3 submit calls, since the second one initially failed, and we're
      // now re-submitting it again
      Mockito.verify(executor, Mockito.times(3)).submit(Mockito.any(Runnable.class));
    }
  }

  @Nested
  final class CloseTest {
    private final CloseableRepository repository = new CloseableRepository();
    private final BackgroundTaskManager archiver =
        new BackgroundTaskManager(
            1,
            repository,
            LoggerFactory.getLogger(BackgroundTaskManagerTest.class),
            executor,
            List.of(),
            new ArchiverConfiguration());

    @Test
    void shouldCloseExecutorOnClose() {
      // when
      archiver.close();

      // then
      assertThat(executor.isTerminated()).isTrue();
    }

    @Test
    void shouldCloseRepositoryOnClose() {
      // when
      archiver.close();

      // then
      assertThat(repository.isClosed).isTrue();
    }

    @Test
    void shouldNotThrowOnRepositoryCloseError() {
      // given
      repository.exception = new RuntimeException("foo");

      // when
      assertThatCode(archiver::close).doesNotThrowAnyException();
    }

    private static final class CloseableRepository extends NoopArchiverRepository {
      private boolean isClosed;
      private Exception exception;

      @Override
      public void close() throws Exception {
        if (exception != null) {
          throw exception;
        }

        isClosed = true;
      }
    }
  }
}

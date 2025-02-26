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

import io.camunda.exporter.tasks.archiver.ArchiverRepository.NoopArchiverRepository;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.NoopBatchOperationUpdateRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.NoopIncidentUpdateRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.agrona.collections.MutableInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

final class BackgroundTaskManagerTest {
  @AutoClose("shutdownNow")
  private final ScheduledThreadPoolExecutor executor =
      Mockito.spy(new ScheduledThreadPoolExecutor(1));

  @Nested
  final class StartTest {
    private final BackgroundTaskManager archiver =
        new BackgroundTaskManager(
            1,
            new NoopArchiverRepository(),
            new NoopIncidentUpdateRepository(),
            new NoopBatchOperationUpdateRepository(),
            LoggerFactory.getLogger(BackgroundTaskManagerTest.class),
            executor,
            // return unfinished futures to have a deterministic count of submitted tasks
            List.of(CompletableFuture::new, CompletableFuture::new),
            Duration.ofMillis(100));

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
    private final CloseableArchiverRepository archiverRepository =
        new CloseableArchiverRepository();
    private final CloseableIncidentRepository incidentRepository =
        new CloseableIncidentRepository();
    private final CloseableBatchOperationUpdateRepository batchOperationUpdateRepository =
        new CloseableBatchOperationUpdateRepository();
    private volatile boolean taskClosed = false;
    private volatile boolean keepRunningEvenIfClosed = false;
    private final RunnableTask task =
        new RunnableTask() {
          @Override
          public void run() {
            while (!taskClosed || keepRunningEvenIfClosed) {
              try {
                Thread.sleep(1);
              } catch (final InterruptedException ignored) {
                break;
              }
            }
          }

          @Override
          public void close() {
            taskClosed = true;
          }
        };
    private final List<RunnableTask> tasks = List.of(task);
    private final BackgroundTaskManager taskManager =
        new BackgroundTaskManager(
            1,
            archiverRepository,
            incidentRepository,
            batchOperationUpdateRepository,
            LoggerFactory.getLogger(BackgroundTaskManagerTest.class),
            executor,
            tasks,
            Duration.ofMillis(100));

    public CloseTest() {
      taskManager.start();
    }

    @Test
    void shouldCloseTasksOnClose() {
      taskManager.close();
      assertThat(taskClosed).isTrue();
    }

    @Test
    void shouldCloseExecutorOnClose() {
      // when
      taskManager.close();

      // then
      Awaitility.await("Executor is terminated")
          .untilAsserted(() -> assertThat(executor.isTerminated()).isTrue());
    }

    @Test
    void shouldCloseExecutorOnCloseWhenTasksDoNotTerminate() {
      // given
      keepRunningEvenIfClosed = true;
      // when
      taskManager.close();

      // then
      Awaitility.await("Executor is terminated")
          .untilAsserted(() -> assertThat(executor.isTerminated()).isTrue());
    }

    @Test
    void shouldCloseRepositoriesOnClose() {
      // when
      taskManager.close();

      // then
      assertThat(archiverRepository.isClosed).isTrue();
      assertThat(incidentRepository.isClosed).isTrue();
      assertThat(batchOperationUpdateRepository.isClosed).isTrue();
    }

    @Test
    void shouldNotThrowOnArchiverRepositoryCloseError() {
      // given
      archiverRepository.exception = new RuntimeException("foo");

      // when
      assertThatCode(taskManager::close).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnIncidentRepositoryCloseError() {
      // given
      incidentRepository.exception = new RuntimeException("foo");

      // when
      assertThatCode(taskManager::close).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnBatchOperationUpdateRepositoryCloseError() {
      // given
      batchOperationUpdateRepository.exception = new RuntimeException("foo");

      // when
      assertThatCode(taskManager::close).doesNotThrowAnyException();
    }

    private static final class CloseableArchiverRepository extends NoopArchiverRepository {
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

    private static final class CloseableIncidentRepository extends NoopIncidentUpdateRepository {
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

    private static final class CloseableBatchOperationUpdateRepository
        extends NoopBatchOperationUpdateRepository {
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

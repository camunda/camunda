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

import io.camunda.exporter.tasks.archiver.ArchiveBatch.AuditLogCleanupBatch;
import io.camunda.exporter.tasks.archiver.AuditLogArchiverRepository;
import io.camunda.exporter.tasks.archiver.NoopArchiverRepository;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository.NoopBatchOperationUpdateRepository;
import io.camunda.exporter.tasks.historydeletion.HistoryDeletionRepository.NoopHistoryDeletionRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository.NoopIncidentUpdateRepository;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

final class CamundaBackgroundTaskManagerTest {
  @AutoClose("shutdownNow")
  private final ScheduledThreadPoolExecutor executor =
      Mockito.spy(new ScheduledThreadPoolExecutor(1));

  @Nested
  final class CloseTest {
    private final CloseableArchiverRepository archiverRepository =
        new CloseableArchiverRepository();
    private final CloseableAuditLogArchiverRepository auditLogArchiverRepository =
        new CloseableAuditLogArchiverRepository();
    private final CloseableIncidentRepository incidentRepository =
        new CloseableIncidentRepository();
    private final CloseableBatchOperationUpdateRepository batchOperationUpdateRepository =
        new CloseableBatchOperationUpdateRepository();
    private final CloseableHistoryDeletionRepository historyDeletionRepository =
        new CloseableHistoryDeletionRepository();
    private final CamundaBackgroundTaskManager taskManager =
        new CamundaBackgroundTaskManager(
            1,
            archiverRepository,
            auditLogArchiverRepository,
            incidentRepository,
            batchOperationUpdateRepository,
            historyDeletionRepository,
            LoggerFactory.getLogger(CamundaBackgroundTaskManagerTest.class),
            executor,
            java.util.List.of(),
            Duration.ofMillis(100));

    public CloseTest() {
      taskManager.start();
    }

    @Test
    void shouldCloseRepositoriesOnClose() {
      // when
      taskManager.close();

      // then
      assertThat(archiverRepository.isClosed).isTrue();
      assertThat(auditLogArchiverRepository.isClosed).isTrue();
      assertThat(incidentRepository.isClosed).isTrue();
      assertThat(batchOperationUpdateRepository.isClosed).isTrue();
      assertThat(historyDeletionRepository.isClosed).isTrue();
    }

    @Test
    void shouldNotThrowOnArchiverRepositoryCloseError() {
      // given
      archiverRepository.exception = new RuntimeException("foo");

      // when
      assertThatCode(taskManager::close).doesNotThrowAnyException();
    }

    @Test
    void shouldNotThrowOnAuditLogArchiverRepositoryCloseError() {
      // given
      auditLogArchiverRepository.exception = new RuntimeException("foo");

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

    @Test
    void shouldNotThrowOnHistoryDeletionRepositoryCloseError() {
      // given
      historyDeletionRepository.exception = new RuntimeException("foo");

      // when
      assertThatCode(taskManager::close).doesNotThrowAnyException();
    }

    private static final class CloseableAuditLogArchiverRepository
        implements AuditLogArchiverRepository {
      private boolean isClosed;
      private Exception exception;

      @Override
      public CompletableFuture<AuditLogCleanupBatch> getNextBatch() {
        return CompletableFuture.completedFuture(
            new AuditLogCleanupBatch(null, java.util.List.of(), java.util.List.of()));
      }

      @Override
      public CompletableFuture<Integer> deleteAuditLogCleanupMetadata(
          final AuditLogCleanupBatch batch) {
        return CompletableFuture.completedFuture(0);
      }

      @Override
      public void close() throws Exception {
        if (exception != null) {
          throw exception;
        }

        isClosed = true;
      }
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

    private static final class CloseableHistoryDeletionRepository
        extends NoopHistoryDeletionRepository {
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

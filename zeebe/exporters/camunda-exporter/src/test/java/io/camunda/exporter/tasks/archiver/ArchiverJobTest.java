/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.exporter.tasks.archiver.TestRepository.DocumentMove;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ArchiverJobTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverJobTest.class);

  private static final String SOURCE_INDEX_NAME = "test-index_";
  private static final String ID_FIELD_NAME = "id-field";

  private final Executor executor = Executors.newSingleThreadExecutor();
  private final Semaphore reindexSemaphore = new Semaphore(Integer.MAX_VALUE);

  private final TestRepository repository = new TestRepository();
  private final CamundaExporterMetrics metrics = mock(CamundaExporterMetrics.class);

  @SuppressWarnings("unchecked")
  private final Consumer<Integer> recordArchiving = mock(Consumer.class);

  @SuppressWarnings("unchecked")
  private final Consumer<Integer> recordArchived = mock(Consumer.class);

  @Test
  void shouldReturnZeroIfNoBatchGiven() {
    // given no batch
    final IdxTemplateArchiver job = getArchiverJob(reindexSemaphore);
    repository.batch = null;

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(0);
    assertThat(repository.moves).isEmpty();

    // then verify recording metrics
    verifyNoInteractions(recordArchiving);
    verifyNoInteractions(recordArchived);
    verify(metrics).measureArchivingDuration(any());
  }

  @Test
  void shouldReturnZeroIfNoBatchIdsGiven() {
    // given
    final IdxTemplateArchiver job = getArchiverJob(reindexSemaphore);
    repository.batch = new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of());

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(0);
    assertThat(repository.moves).isEmpty();

    // then verify recording metrics
    verifyNoInteractions(recordArchiving);
    verifyNoInteractions(recordArchived);
    verify(metrics).measureArchivingDuration(any());
  }

  @Test
  void shouldMoveInstancesById() {
    // given
    final IdxTemplateArchiver job = getArchiverJob(reindexSemaphore);
    repository.batch = new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // when
    final int count = job.execute().toCompletableFuture().join();

    // then
    assertThat(count).isEqualTo(3);
    assertThat(repository.moves)
        .containsExactly(
            new DocumentMove(
                SOURCE_INDEX_NAME,
                SOURCE_INDEX_NAME + "2024-01-01",
                Map.of(ID_FIELD_NAME, List.of("1", "2", "3")),
                executor));

    // then verify recording metrics
    verify(recordArchiving).accept(3);
    verify(recordArchived).accept(3);
    verify(metrics).measureArchivingDuration(any());
  }

  @Test
  void whenThrottlingWithSemaphore() {
    // given - start with 0 permits so both archives block until we release
    final Semaphore limitingSemaphore = new Semaphore(0);
    final Executor multiThreadExecutor = Executors.newFixedThreadPool(2);
    final ArchiverRepository archiverRepository = mock(ArchiverRepository.class);
    final BasicArchiveBatch batch = new BasicArchiveBatch("2024-01-01", List.of("1", "2", "3"));

    // given - setup jobs & response
    final IdxTemplateArchiver firstJob =
        getArchiverJob(limitingSemaphore, archiverRepository, multiThreadExecutor);
    final CompletableFuture<Integer> firstJobFuture =
        firstJob.archive(firstJob.getTemplateDescriptor(), batch);

    final IdxTemplateArchiver secondJob =
        getArchiverJob(limitingSemaphore, archiverRepository, multiThreadExecutor);
    final CompletableFuture<Integer> secondJobFuture =
        secondJob.archive(secondJob.getTemplateDescriptor(), batch);

    final CompletableFuture<Void> jobOneResponse = new CompletableFuture<>();
    final CompletableFuture<Void> jobTwoResponse = new CompletableFuture<>();
    when(archiverRepository.moveDocuments(any(), any(), any(), any(), any()))
        .thenReturn(jobOneResponse)
        .thenReturn(jobTwoResponse);

    // when - trigger both archive futures without blocking
    final CompletableFuture<Object> eitherDone =
        CompletableFuture.anyOf(firstJobFuture, secondJobFuture);

    // then - neither should have completed yet since no permits are available
    assertThat(firstJobFuture).isNotDone();
    assertThat(secondJobFuture).isNotDone();
    assertThat(limitingSemaphore.availablePermits()).isEqualTo(0);

    // when - release one permit, allowing exactly one archive to proceed
    limitingSemaphore.release();

    // eventually no permits should be available since the released permit should be
    // acquired by the job waiting for repository
    Awaitility.await("Either job should complete after releasing one permit")
        .timeout(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(limitingSemaphore.availablePermits()).isEqualTo(0));

    // then - return first repository response
    jobOneResponse.complete(null);

    // then ensure one job is done
    assertThat(eitherDone).succeedsWithin(java.time.Duration.ofSeconds(5));

    // then - return second repository response
    jobTwoResponse.complete(null);

    // then - both should complete
    assertThat(firstJobFuture).succeedsWithin(java.time.Duration.ofSeconds(5));
    assertThat(secondJobFuture).succeedsWithin(java.time.Duration.ofSeconds(5));
    assertThat(firstJobFuture.join()).isEqualTo(3);
    assertThat(secondJobFuture.join()).isEqualTo(3);

    // then - verify that the semaphore is released after both complete
    assertThat(limitingSemaphore.availablePermits()).isEqualTo(1);
  }

  private IdxTemplateArchiver getArchiverJob(final Semaphore reindexSemaphore) {
    return getArchiverJob(reindexSemaphore, repository, executor);
  }

  private IdxTemplateArchiver getArchiverJob(
      final Semaphore reindexSemaphore,
      final ArchiverRepository archiverRepository,
      final Executor executor) {
    return new IdxTemplateArchiver(
        archiverRepository,
        metrics,
        LOGGER,
        executor,
        reindexSemaphore,
        recordArchiving,
        recordArchived);
  }

  static class IdxTemplateArchiver extends ArchiverJob<ArchiveBatch.BasicArchiveBatch> {

    private final IndexTemplateDescriptor indexTemplateDescriptor;

    public IdxTemplateArchiver(
        final ArchiverRepository archiverRepository,
        final CamundaExporterMetrics exporterMetrics,
        final Logger logger,
        final Executor executor,
        final Semaphore reindexSemaphore,
        final Consumer<Integer> recordArchivingMetric,
        final Consumer<Integer> recordArchivedMetric) {
      super(
          archiverRepository,
          exporterMetrics,
          logger,
          executor,
          reindexSemaphore,
          recordArchivingMetric,
          recordArchivedMetric);
      indexTemplateDescriptor = mock(IndexTemplateDescriptor.class);
      when(indexTemplateDescriptor.getIdField()).thenReturn(ID_FIELD_NAME);
      when(indexTemplateDescriptor.getFullQualifiedName()).thenReturn(SOURCE_INDEX_NAME);
    }

    public IdxTemplateArchiver(
        final ArchiverRepository archiverRepository,
        final CamundaExporterMetrics exporterMetrics,
        final Logger logger,
        final Executor executor,
        final Semaphore reindexSemaphore,
        final Consumer<Integer> recordArchivingMetric,
        final Consumer<Integer> recordArchivedMetric,
        final String sourceIndexName) {
      super(
          archiverRepository,
          exporterMetrics,
          logger,
          executor,
          reindexSemaphore,
          recordArchivingMetric,
          recordArchivedMetric);
      indexTemplateDescriptor = mock(IndexTemplateDescriptor.class);
      when(indexTemplateDescriptor.getIdField()).thenReturn(ID_FIELD_NAME);
      when(indexTemplateDescriptor.getFullQualifiedName()).thenReturn(sourceIndexName);
    }

    @Override
    protected String getJobName() {
      return "test-archiver";
    }

    @Override
    CompletableFuture<ArchiveBatch.BasicArchiveBatch> getNextBatch() {
      return ((TestRepository) getArchiverRepository()).getNextBatch();
    }

    @Override
    IndexTemplateDescriptor getTemplateDescriptor() {
      return indexTemplateDescriptor;
    }

    @Override
    protected Map<String, List<String>> createIdsByFieldMap(
        final IndexTemplateDescriptor indexTemplateDescriptor, final BasicArchiveBatch batch) {
      return Map.of(indexTemplateDescriptor.getIdField(), batch.ids());
    }
  }
}

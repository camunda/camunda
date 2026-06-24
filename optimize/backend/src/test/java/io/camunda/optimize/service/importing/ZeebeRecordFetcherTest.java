/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.importing.zeebe.db.ZeebeProcessInstanceFetcher;
import io.camunda.optimize.service.importing.zeebe.fetcher.es.ZeebeProcessInstanceFetcherES;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ZeebeRecordFetcherTest {

  public static final int TEST_CONFIGURED_BATCH_SIZE = 5;
  @Mock ObjectMapper objectMapper;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;

  @Mock SearchResponse searchResponse;
  // We test using a single specific implementation of the abstract record fetcher
  private ZeebeProcessInstanceFetcher underTest;
  @Mock private OptimizeElasticsearchClient optimizeElasticsearchClient;

  @Test
  public void testFetchFailsTriggersDynamicBatchResizing() {
    // given
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize())
        .thenReturn(TEST_CONFIGURED_BATCH_SIZE);
    when(configurationService
            .getConfiguredZeebe()
            .getImportConfig()
            .getDynamicBatchSuccessAttempts())
        .thenReturn(10);
    initalizeClassUnderTest();
    // the class is initialized with the default batch size and number of successful requests
    assertThat(underTest.getDynamicBatchSize()).isEqualTo(TEST_CONFIGURED_BATCH_SIZE);
    assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
    // and search requests fail with an IOException
    try {
      when(optimizeElasticsearchClient.searchWithoutPrefixing(any(), any()))
          .thenThrow(IOException.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
    }

    // when the next import is attempted
    triggerFailedFetchAttempt();

    // then the batch size has been dynamically reduced, and we cache the batch size in the deque
    assertThat(underTest.getDynamicBatchSize()).isEqualTo(2);
    assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
    assertThat(underTest.getBatchSizeDeque()).containsExactly(2);

    // when the next import is attempted
    triggerFailedFetchAttempt();

    // then the batch size has been dynamically reduced, and we cache the batch size in the deque
    assertThat(underTest.getDynamicBatchSize()).isEqualTo(1);
    assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
    assertThat(underTest.getBatchSizeDeque()).containsExactly(1, 2);

    // when the next import is attempted
    triggerFailedFetchAttempt();

    // then the batch size remains the same, as it must always be at least 1
    assertThat(underTest.getDynamicBatchSize()).isEqualTo(1);
    assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
    // and the deque doesn't contain duplicate entries
    assertThat(underTest.getBatchSizeDeque()).containsExactly(1, 2);

    // given that search is successful
    try (final MockedStatic<ElasticsearchReaderUtil> mockEsReaderUtil =
        Mockito.mockStatic(ElasticsearchReaderUtil.class)) {
      mockEsReaderUtil
          .when(() -> ElasticsearchReaderUtil.mapHits(any(), any(), any()))
          .thenReturn(List.of());
      Mockito.reset(optimizeElasticsearchClient);
      final ShardStatistics mockedShardStatistics = mock(ShardStatistics.class);
      when(mockedShardStatistics.failures()).thenReturn(List.of());
      when(mockedShardStatistics.total()).thenReturn(0);
      when(mockedShardStatistics.failed()).thenReturn(0);
      when(mockedShardStatistics.successful()).thenReturn(0);
      when(searchResponse.shards()).thenReturn(mockedShardStatistics);
      try {
        when(optimizeElasticsearchClient.searchWithoutPrefixing(any(), any()))
            .thenReturn(searchResponse);
      } catch (final IOException e) {
        throw new OptimizeRuntimeException(e);
      }

      // when the next import is attempted
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage());

      // then the batch size remains the same but the consecutive successful batch count has been
      // incremented
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(1);
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isEqualTo(1);
      // and the deque contains the previously attempted failed batch sizes
      assertThat(underTest.getBatchSizeDeque()).containsExactly(1, 2);

      // when the next import is a successful a further 9 times
      IntStream.range(0, 9)
          .forEach(
              batch ->
                  underTest.getZeebeRecordsForPrefixAndPartitionFrom(
                      new PositionBasedImportPage()));

      // then the batch size has been reverted to the previously failed batch size (still 1)
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(1);
      // and the counter of successive fetches is reset to 0
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
      // and the deque no longer contains the new batch size
      assertThat(underTest.getBatchSizeDeque()).containsExactly(2);

      // when the next import is attempted
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage());

      // then the batch size is increased to the cached value
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(1);
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isEqualTo(1);
      // and the deque only contains the previously attempted batch size
      assertThat(underTest.getBatchSizeDeque()).containsExactly(2);

      // when the next import is a successful a further 9 times
      IntStream.range(0, 9)
          .forEach(
              batch ->
                  underTest.getZeebeRecordsForPrefixAndPartitionFrom(
                      new PositionBasedImportPage()));

      // then the batch size has been reverted to the previous batch size
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(2);
      // and the counter of successive fetches is reset to 0
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
      // and the deque remains empty
      assertThat(underTest.getBatchSizeDeque()).isEmpty();

      // when the next import is attempted
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage());

      // then the batch size stays at the previous batch size
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(2);
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isEqualTo(1);
      // and the deque is empty
      assertThat(underTest.getBatchSizeDeque()).isEmpty();

      // when the next import is a successful a further 9 times
      IntStream.range(0, 9)
          .forEach(
              batch ->
                  underTest.getZeebeRecordsForPrefixAndPartitionFrom(
                      new PositionBasedImportPage()));

      // then the batch size is increased back to the default size
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(TEST_CONFIGURED_BATCH_SIZE);
      // and the counter of successive fetches is reset to 0
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
      // and the deque remains empty
      assertThat(underTest.getBatchSizeDeque()).isEmpty();

      // when the next import is attempted
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage());

      // then the default size is still being used
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(TEST_CONFIGURED_BATCH_SIZE);
      // and the consecutive attempt count is no longer incremented as the default size is being
      // used
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
      // and the deque remains empty
      assertThat(underTest.getBatchSizeDeque()).isEmpty();
    }
  }

  @Test
  public void testThatEmptyPageFetchesAreTrackedCorrectly() {
    // given
    when(configurationService.getConfiguredZeebe().getImportConfig().getMaxEmptyPagesToImport())
        .thenReturn(3);
    initalizeClassUnderTest();
    // the class is initialized with the number of empty pages set to zero
    assertThat(underTest.getConsecutiveEmptyPages()).isZero();

    // given that search is successfully executed but returning empty pages
    try (final MockedStatic<ElasticsearchReaderUtil> mockEsReaderUtil =
        Mockito.mockStatic(ElasticsearchReaderUtil.class)) {
      mockEsReaderUtil
          .when(() -> ElasticsearchReaderUtil.mapHits(any(), any(), any()))
          .thenReturn(List.of());
      Mockito.reset(optimizeElasticsearchClient);
      final ShardStatistics mockedShardStatistics = mock(ShardStatistics.class);
      when(mockedShardStatistics.failures()).thenReturn(List.of());
      when(mockedShardStatistics.total()).thenReturn(0);
      when(mockedShardStatistics.failed()).thenReturn(0);
      when(mockedShardStatistics.successful()).thenReturn(0);
      when(searchResponse.shards()).thenReturn(mockedShardStatistics);
      try {
        when(optimizeElasticsearchClient.searchWithoutPrefixing(any(), any()))
            .thenReturn(searchResponse);
      } catch (final IOException e) {
        throw new OptimizeRuntimeException(e);
      }

      // when the next import is attempted
      triggerFetchAttemptForEmptyPage();

      // then the number of empty pages has been incremented
      assertThat(underTest.getConsecutiveEmptyPages()).isEqualTo(1);

      // when the next import is attempted
      triggerFetchAttemptForEmptyPage();

      // then the number of empty pages has been incremented
      assertThat(underTest.getConsecutiveEmptyPages()).isEqualTo(2);

      // when the next import is attempted
      triggerFetchAttemptForEmptyPage();

      // then the number of empty pages has been incremented
      assertThat(underTest.getConsecutiveEmptyPages()).isEqualTo(3);

      // when the next import is attempted
      triggerFetchAttemptForEmptyPage();

      // then the number of empty pages has been reset to zero
      assertThat(underTest.getConsecutiveEmptyPages()).isZero();

      // when the next import is attempted
      triggerFetchAttemptForEmptyPage();

      // then the number of empty pages has been incremented
      assertThat(underTest.getConsecutiveEmptyPages()).isEqualTo(1);

      // when a non-empty page of results is imported
      mockEsReaderUtil
          .when(() -> ElasticsearchReaderUtil.mapHits(any(), any(), any()))
          .thenReturn(List.of(new ZeebeVariableRecordDto()));
      triggerFetchAttempt();

      // then the number of empty pages has been reset to zero
      assertThat(underTest.getConsecutiveEmptyPages()).isZero();
    }
  }

  private void initalizeClassUnderTest() {
    underTest =
        new ZeebeProcessInstanceFetcherES(
            1, optimizeElasticsearchClient, objectMapper, configurationService);
  }

  private void triggerFailedFetchAttempt() {
    final PositionBasedImportPage positionBasedImportPage = new PositionBasedImportPage();
    assertThatExceptionOfType(OptimizeRuntimeException.class)
        .isThrownBy(
            () -> underTest.getZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage));
  }

  private void triggerFetchAttemptForEmptyPage() {
    final PositionBasedImportPage positionBasedImportPage = new PositionBasedImportPage();
    positionBasedImportPage.setHasSeenSequenceField(true);
    assertThat(underTest.getZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage))
        .isEmpty();
  }

  private void triggerFetchAttempt() {
    final PositionBasedImportPage positionBasedImportPage = new PositionBasedImportPage();
    positionBasedImportPage.setHasSeenSequenceField(true);
    underTest.getZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage);
  }
}

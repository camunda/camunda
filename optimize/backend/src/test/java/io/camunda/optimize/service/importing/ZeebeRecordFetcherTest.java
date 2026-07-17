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
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch._types.ShardStatistics;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
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

  /**
   * Values that bracket the {@code double} precision boundary. Beyond 2^53 not every {@code long}
   * has an exact {@code double} representation, and the gap between representable values doubles
   * every power of two (2 between 2^53-2^54, 4 up to 2^55, 16 in the ~2^56 band, ...). Zeebe
   * sequences/positions are generated as {@code partitionId << 51 | counter}, so on clusters with
   * more than 3 partitions they exceed 2^53 and a lossy {@code double} conversion rounds them off.
   *
   * <p>Each argument carries whether the value genuinely loses precision as a {@code double}, so
   * the tests can self-check that the "large" values actually exercise the rounding (see #58064).
   */
  private static Stream<Arguments> boundaryLongValues() {
    return Stream.of(
        arguments(123L, false), // below 2^53, exactly representable — control case
        arguments((1L << 53) + 1, true), // just above 2^53, gap 2 — rounds
        arguments(75309396599875985L, true), // ~2^56 band (gap 16) — rounds DOWN (-1)
        arguments(
            75309396599875993L, true)); // ~2^56 band (gap 16) — rounds UP (+7): real skip case
  }

  @ParameterizedTest(name = "sequence={0}")
  @MethodSource("boundaryLongValues")
  public void shouldBuildSequenceQueryWithExactLongBounds(
      final long lastImportedSequence, final boolean losesPrecisionAsDouble) {
    // given
    final int batchSize = 400;
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize()).thenReturn(batchSize);
    // keep consecutiveEmptyPages (0) < max so getRecordQuery builds the sequence query, not the
    // position-query fallback
    when(configurationService.getConfiguredZeebe().getImportConfig().getMaxEmptyPagesToImport())
        .thenReturn(1);
    initalizeClassUnderTest();

    final PositionBasedImportPage positionBasedImportPage = new PositionBasedImportPage();
    positionBasedImportPage.setHasSeenSequenceField(true);
    positionBasedImportPage.setSequence(lastImportedSequence);

    try (final MockedStatic<ElasticsearchReaderUtil> mockEsReaderUtil =
        mockEmptyReaderAndShards()) {
      final ArgumentCaptor<SearchRequest> requestCaptor =
          ArgumentCaptor.forClass(SearchRequest.class);
      captureSearchRequest(requestCaptor);

      // when
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage);

      // then the exact long bounds must reach Elasticsearch. Building the range via
      // Double#doubleValue() (as the ES fetcher did before this fix) rounds any value beyond 2^53
      // and can jump the lower bound past not-yet-imported records, skipping them forever.
      final var sequenceRange =
          requestCaptor.getValue().query().bool().must().stream()
              .filter(Query::isRange)
              .map(q -> q.range().untyped())
              .findFirst()
              .orElseThrow();
      assertThat(sequenceRange.gt().to(Long.class)).isEqualTo(lastImportedSequence);
      assertThat(sequenceRange.lte().to(Long.class)).isEqualTo(lastImportedSequence + batchSize);

      assertValueGenuinelyExercisesRounding(lastImportedSequence, losesPrecisionAsDouble);
    }
  }

  @ParameterizedTest(name = "position={0}")
  @MethodSource("boundaryLongValues")
  public void shouldBuildPositionQueryWithExactLongBounds(
      final long lastImportedPosition, final boolean losesPrecisionAsDouble) {
    // given
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize()).thenReturn(400);
    initalizeClassUnderTest();

    final PositionBasedImportPage positionBasedImportPage = new PositionBasedImportPage();
    // hasSeenSequenceField=false routes getRecordQuery through buildPositionQuery
    positionBasedImportPage.setPosition(lastImportedPosition);

    try (final MockedStatic<ElasticsearchReaderUtil> mockEsReaderUtil =
        mockEmptyReaderAndShards()) {
      final ArgumentCaptor<SearchRequest> requestCaptor =
          ArgumentCaptor.forClass(SearchRequest.class);
      captureSearchRequest(requestCaptor);

      // when
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage);

      // then the exact long position bound must reach Elasticsearch (see the sequence-query test
      // for why a lossy double bound silently skips records).
      final var positionRange =
          requestCaptor.getValue().query().bool().must().stream()
              .filter(Query::isRange)
              .map(q -> q.range().untyped())
              .findFirst()
              .orElseThrow();
      assertThat(positionRange.gt().to(Long.class)).isEqualTo(lastImportedPosition);

      assertValueGenuinelyExercisesRounding(lastImportedPosition, losesPrecisionAsDouble);
    }
  }

  // Guards the boundaryLongValues source against silently rotting: if a "large" value were ever
  // swapped for one that is exactly representable as a double, the bound assertions above would
  // pass even against the lossy implementation and stop protecting against the regression.
  private static void assertValueGenuinelyExercisesRounding(
      final long value, final boolean losesPrecisionAsDouble) {
    if (losesPrecisionAsDouble) {
      assertThat((long) (double) value).isNotEqualTo(value);
    } else {
      assertThat((long) (double) value).isEqualTo(value);
    }
  }

  private MockedStatic<ElasticsearchReaderUtil> mockEmptyReaderAndShards() {
    final MockedStatic<ElasticsearchReaderUtil> mockEsReaderUtil =
        Mockito.mockStatic(ElasticsearchReaderUtil.class);
    mockEsReaderUtil
        .when(() -> ElasticsearchReaderUtil.mapHits(any(), any(), any()))
        .thenReturn(List.of());
    final ShardStatistics mockedShardStatistics = mock(ShardStatistics.class);
    when(mockedShardStatistics.failures()).thenReturn(List.of());
    when(mockedShardStatistics.total()).thenReturn(0);
    when(mockedShardStatistics.failed()).thenReturn(0);
    when(mockedShardStatistics.successful()).thenReturn(0);
    when(searchResponse.shards()).thenReturn(mockedShardStatistics);
    return mockEsReaderUtil;
  }

  private void captureSearchRequest(final ArgumentCaptor<SearchRequest> requestCaptor) {
    try {
      when(optimizeElasticsearchClient.searchWithoutPrefixing(requestCaptor.capture(), any()))
          .thenReturn(searchResponse);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(e);
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

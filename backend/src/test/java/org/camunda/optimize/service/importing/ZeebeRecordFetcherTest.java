/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.page.PositionBasedImportPage;
import org.camunda.optimize.service.importing.zeebe.fetcher.ZeebeProcessInstanceFetcher;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ZeebeRecordFetcherTest {

  public static final int TEST_CONFIGURED_BATCH_SIZE = 5;
  // We test using a single specific implementation of the abstract record fetcher
  private ZeebeProcessInstanceFetcher underTest;

  @Mock
  private OptimizeElasticsearchClient optimizeElasticsearchClient;
  @Mock
  ObjectMapper objectMapper;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;
  @Mock
  SearchResponse searchResponse;

  @BeforeEach
  public void init() {
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize()).thenReturn(TEST_CONFIGURED_BATCH_SIZE);
    this.underTest = new ZeebeProcessInstanceFetcher(
      1,
      optimizeElasticsearchClient,
      objectMapper,
      configurationService
    );
  }

  @Test
  @SneakyThrows
  public void testFetchFailsTriggersDynamicBatchResizing() {
    // given the class is initialized with the default batch size and number of successful requests
    assertThat(underTest.getDynamicBatchSize()).isEqualTo(TEST_CONFIGURED_BATCH_SIZE);
    assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
    // and search requests fail with an IOException
    when(optimizeElasticsearchClient.searchWithoutPrefixing(any())).thenThrow(IOException.class);

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
    try (MockedStatic<ElasticsearchReaderUtil> mockEsReaderUtil = Mockito.mockStatic(ElasticsearchReaderUtil.class)) {
      mockEsReaderUtil.when(() -> ElasticsearchReaderUtil.mapHits(any(), any(), any())).thenReturn(List.of());
      Mockito.reset(optimizeElasticsearchClient);
      when(optimizeElasticsearchClient.searchWithoutPrefixing(any())).thenReturn(searchResponse);

      // when the next import is attempted
      underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage());

      // then the batch size remains the same but the consecutive successful batch count has been incremented
      assertThat(underTest.getDynamicBatchSize()).isEqualTo(1);
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isEqualTo(1);
      // and the deque contains the previously attempted failed batch sizes
      assertThat(underTest.getBatchSizeDeque()).containsExactly(1, 2);

      // when the next import is a successful a further 9 times
      IntStream.range(0, 9)
        .forEach(batch -> underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage()));

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
        .forEach(batch -> underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage()));

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
        .forEach(batch -> underTest.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage()));

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
      // and the consecutive attempt count is no longer incremented as the default size is being used
      assertThat(underTest.getConsecutiveSuccessfulFetches()).isZero();
      // and the deque remains empty
      assertThat(underTest.getBatchSizeDeque()).isEmpty();
    }
  }

  private void triggerFailedFetchAttempt() {
    final PositionBasedImportPage positionBasedImportPage = new PositionBasedImportPage();
    assertThrows(
      OptimizeRuntimeException.class, () -> underTest.getZeebeRecordsForPrefixAndPartitionFrom(positionBasedImportPage));
  }

}

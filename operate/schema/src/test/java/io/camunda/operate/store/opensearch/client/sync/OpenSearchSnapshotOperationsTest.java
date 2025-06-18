/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import static io.camunda.operate.store.opensearch.response.SnapshotState.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.store.opensearch.response.OpenSearchSnapshotInfo;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.opensearch.client.opensearch.snapshot.GetSnapshotRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchSnapshotOperationsTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpenSearchSnapshotOperationsTest.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private ExtendedOpenSearchClient openSearchClient;
  private OpenSearchSnapshotOperations snapshotOperations;
  private GetSnapshotRequest request;

  @BeforeEach
  void setUp() {
    openSearchClient = mock(ExtendedOpenSearchClient.class);
    snapshotOperations = new OpenSearchSnapshotOperations(LOGGER, openSearchClient);
    request = mock(GetSnapshotRequest.class, Answers.RETURNS_DEEP_STUBS);
  }

  @Test
  void testGetWithLongFieldsShouldSucceed() throws IOException {
    // given
    final var snapshotMap = new HashMap<>();
    snapshotMap.put("snapshot", "snap1");
    snapshotMap.put("uuid", "uuid-1");
    snapshotMap.put("state", "SUCCESS");
    snapshotMap.put("start_time_in_millis", 1000L);
    snapshotMap.put("end_time_in_millis", 2000L);
    snapshotMap.put("metadata", Map.of());
    snapshotMap.put("failures", List.of());
    when(openSearchClient.arbitraryRequest(any(), any(), any()))
        .thenReturn(Map.of("snapshots", List.of(snapshotMap)));

    // when
    final var response = snapshotOperations.get(request);

    // then
    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().getFirst().getStartTimeInMillis()).isEqualTo(1000L);
    assertThat(response.snapshots().getFirst().getEndTimeInMillis()).isEqualTo(2000L);
  }

  @Test
  void testGetWithStringMillisShouldSucceed() throws IOException {
    // given
    final Map<String, Object> snapshotMap = new HashMap<>();
    snapshotMap.put("snapshot", "snap1");
    snapshotMap.put("uuid", "uuid-1");
    snapshotMap.put("state", "SUCCESS");
    snapshotMap.put("start_time_in_millis", "1000");
    snapshotMap.put("end_time_in_millis", "2000");
    snapshotMap.put("metadata", Map.of());
    snapshotMap.put("failures", List.of());
    when(openSearchClient.arbitraryRequest(any(), any(), any()))
        .thenReturn(Map.of("snapshots", List.of(snapshotMap)));

    // when
    final var response = snapshotOperations.get(request);

    // then
    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().getFirst().getStartTimeInMillis()).isEqualTo(1000L);
    assertThat(response.snapshots().getFirst().getEndTimeInMillis()).isEqualTo(2000L);
  }

  @Test
  void testGetWithIntegerMillisShouldSucceed() throws IOException {
    // given
    final Map<String, Object> snapshotMap = new HashMap<>();
    snapshotMap.put("snapshot", "snap1");
    snapshotMap.put("uuid", "uuid-1");
    snapshotMap.put("state", "SUCCESS");
    snapshotMap.put("start_time_in_millis", 1000); // Integer
    snapshotMap.put("end_time_in_millis", 2000); // Integer
    snapshotMap.put("metadata", Map.of());
    snapshotMap.put("failures", List.of());
    when(openSearchClient.arbitraryRequest(any(), any(), any()))
        .thenReturn(Map.of("snapshots", List.of(snapshotMap)));

    // when
    final var response = snapshotOperations.get(request);

    // then
    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().getFirst().getStartTimeInMillis()).isEqualTo(1000L);
    assertThat(response.snapshots().getFirst().getEndTimeInMillis()).isEqualTo(2000L);
  }

  @Test
  void testGetWithNullMillisShouldSucceed() throws IOException {
    // given
    final Map<String, Object> snapshotMap = new HashMap<>();
    snapshotMap.put("snapshot", "snap1");
    snapshotMap.put("uuid", "uuid-1");
    snapshotMap.put("state", "SUCCESS");
    snapshotMap.put("metadata", Map.of());
    snapshotMap.put("failures", List.of());
    when(openSearchClient.arbitraryRequest(any(), any(), any()))
        .thenReturn(Map.of("snapshots", List.of(snapshotMap)));

    // when
    final var response = snapshotOperations.get(request);

    // then
    assertThat(response.snapshots()).hasSize(1);
    assertThat(response.snapshots().getFirst().getStartTimeInMillis()).isNull();
    assertThat(response.snapshots().getFirst().getEndTimeInMillis()).isNull();
  }

  @Test
  void testGetWithJsonSnapshotShouldSucceed() throws Exception {
    // example JSON response from OpenSearch taken from
    // https://docs.opensearch.org/docs/latest/api-reference/snapshots/get-snapshot/#example-response
    // modified by setting end_time_in_millis to 0 to test the conversion from Integer to Long
    final String json =
        """
      {
      "snapshots" : [
        {
          "snapshot" : "my-first-snapshot",
          "uuid" : "3P7Qa-M8RU6l16Od5n7Lxg",
          "version_id" : 136217927,
          "version" : "2.0.1",
          "indices" : [
            ".opensearch-observability",
            ".opendistro-reports-instances",
            ".opensearch-notifications-config",
            "shakespeare",
            ".opendistro-reports-definitions",
            "opensearch_dashboards_sample_data_flights",
            ".kibana_1"
          ],
          "data_streams" : [ ],
          "include_global_state" : true,
          "state" : "SUCCESS",
          "start_time" : "2022-08-11T20:30:00.399Z",
          "start_time_in_millis" : 1660249800399,
          "end_time" : "2022-08-11T20:30:14.851Z",
          "end_time_in_millis" : 0,
          "duration_in_millis" : 14452,
          "failures" : [ ],
          "shards" : {
            "total" : 7,
            "failed" : 0,
            "successful" : 7
          }
        }
      ]
    }
    """;

    final Map<String, Object> deserialized = objectMapper.readValue(json, Map.class);
    when(openSearchClient.arbitraryRequest(any(), any(), any())).thenReturn(deserialized);

    final var response = snapshotOperations.get(request);
    assertThat(response.snapshots()).hasSize(1);
    final OpenSearchSnapshotInfo snapshot = response.snapshots().getFirst();
    assertThat(snapshot.getSnapshot()).isEqualTo("my-first-snapshot");
    assertThat(snapshot.getState()).isEqualTo(SUCCESS);
    assertThat(snapshot.getStartTimeInMillis()).isEqualTo(1660249800399L);
    assertThat(snapshot.getEndTimeInMillis()).isEqualTo(0L);
  }
}

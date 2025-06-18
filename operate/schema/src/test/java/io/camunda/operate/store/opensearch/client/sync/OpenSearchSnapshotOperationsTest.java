/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
  private GetSnapshotRequest.Builder request;

  @BeforeEach
  void setUp() {
    openSearchClient = mock(ExtendedOpenSearchClient.class);
    snapshotOperations = new OpenSearchSnapshotOperations(LOGGER, openSearchClient);
    request = mock(GetSnapshotRequest.Builder.class, Answers.RETURNS_DEEP_STUBS);
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
    assertThat(response.snapshots().get(0).getStartTimeInMillis()).isEqualTo(1000L);
    assertThat(response.snapshots().get(0).getEndTimeInMillis()).isEqualTo(2000L);
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
    assertThat(response.snapshots().get(0).getStartTimeInMillis()).isEqualTo(1000L);
    assertThat(response.snapshots().get(0).getEndTimeInMillis()).isEqualTo(2000L);
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
    assertThat(response.snapshots().get(0).getStartTimeInMillis()).isEqualTo(1000L);
    assertThat(response.snapshots().get(0).getEndTimeInMillis()).isEqualTo(2000L);
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
    assertThat(response.snapshots().get(0).getStartTimeInMillis()).isNull();
    assertThat(response.snapshots().get(0).getEndTimeInMillis()).isNull();
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
    final OpenSearchSnapshotInfo snapshot = response.snapshots().get(0);
    assertThat(snapshot.getSnapshot()).isEqualTo("my-first-snapshot");
    assertThat(snapshot.getState()).isEqualTo(SUCCESS);
    assertThat(snapshot.getStartTimeInMillis()).isEqualTo(1660249800399L);
    assertThat(snapshot.getEndTimeInMillis()).isEqualTo(0L);
  }
}

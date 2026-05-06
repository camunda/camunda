/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.fetch.DocumentContentGetRequest;
import io.camunda.process.test.api.judge.ResolvedDocument;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentReferenceResolverTest {

  @Mock private CamundaClient client;
  @Mock private DocumentContentGetRequest request;
  @Mock private CamundaFuture<InputStream> future;

  private DocumentReferenceResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new DocumentReferenceResolver(client);
  }

  @Test
  void shouldReturnEmptyListForNullOrBlank() {
    assertThat(resolver.resolve(null)).isEmpty();
    assertThat(resolver.resolve("")).isEmpty();
  }

  @Test
  void shouldReturnEmptyListForInvalidJson() {
    assertThat(resolver.resolve("{not valid")).isEmpty();
  }

  @Test
  void shouldReturnEmptyListWhenNoReferencesPresent() {
    assertThat(resolver.resolve("{\"foo\": \"bar\"}")).isEmpty();
    assertThat(resolver.resolve("[1, 2, 3]")).isEmpty();
  }

  @Test
  void shouldResolveTopLevelReference() {
    // given
    when(client.newDocumentContentGetRequest("doc-1")).thenReturn(request);
    lenient().when(request.storeId(any())).thenReturn(request);
    lenient().when(request.contentHash(any())).thenReturn(request);
    when(request.send()).thenReturn(future);
    when(future.join()).thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

    final String json =
        "{\"camunda.document.type\": \"camunda\","
            + " \"documentId\": \"doc-1\","
            + " \"contentHash\": \"hash-1\","
            + " \"storeId\": \"store-1\","
            + " \"metadata\": {\"fileName\": \"a.png\", \"contentType\": \"image/png\"}}";

    // when
    final List<ResolvedDocument> resolved = resolver.resolve(json);

    // then
    assertThat(resolved).hasSize(1);
    final ResolvedDocument doc = resolved.get(0);
    assertThat(doc.isResolved()).isTrue();
    assertThat(doc.getDocumentId()).isEqualTo("doc-1");
    assertThat(doc.getFileName()).isEqualTo("a.png");
    assertThat(doc.getContentType()).isEqualTo("image/png");
    assertThat(doc.getData()).containsExactly(1, 2, 3);

    verify(client).newDocumentContentGetRequest("doc-1");
    verify(request).storeId("store-1");
    verify(request).contentHash("hash-1");
  }

  @Test
  void shouldResolveNestedReferencesInArrays() {
    // given
    stubDownload("doc-a", "A".getBytes());
    stubDownload("doc-b", "B".getBytes());

    final String json =
        "{\"messages\": ["
            + "{\"role\": \"tool\", \"result\": "
            + "{\"camunda.document.type\": \"camunda\", \"documentId\": \"doc-a\","
            + " \"metadata\": {\"contentType\": \"text/plain\"}}},"
            + "{\"role\": \"user\", \"attachments\": ["
            + "{\"camunda.document.type\": \"camunda\", \"documentId\": \"doc-b\","
            + " \"metadata\": {\"contentType\": \"text/plain\"}}]}"
            + "]}";

    // when
    final List<ResolvedDocument> resolved = resolver.resolve(json);

    // then
    assertThat(resolved).hasSize(2);
    assertThat(resolved)
        .extracting(ResolvedDocument::getDocumentId)
        .containsExactlyInAnyOrder("doc-a", "doc-b");
  }

  @Test
  void shouldRecordFailureWhenDownloadThrows() {
    // given
    when(client.newDocumentContentGetRequest(anyString())).thenReturn(request);
    lenient().when(request.storeId(any())).thenReturn(request);
    lenient().when(request.contentHash(any())).thenReturn(request);
    when(request.send()).thenReturn(future);
    when(future.join()).thenThrow(new RuntimeException("boom"));

    final String json =
        "{\"camunda.document.type\": \"camunda\", \"documentId\": \"doc-x\","
            + " \"metadata\": {\"fileName\": \"x.bin\", \"contentType\": \"application/octet-stream\"}}";

    // when
    final List<ResolvedDocument> resolved = resolver.resolve(json);

    // then
    assertThat(resolved).hasSize(1);
    final ResolvedDocument doc = resolved.get(0);
    assertThat(doc.isResolved()).isFalse();
    assertThat(doc.getDocumentId()).isEqualTo("doc-x");
    assertThat(doc.getErrorMessage()).contains("boom");
  }

  @Test
  void shouldSkipReferencesWithoutDocumentId() {
    // given
    final String json =
        "{\"camunda.document.type\": \"camunda\","
            + " \"metadata\": {\"contentType\": \"image/png\"}}";

    // when
    final List<ResolvedDocument> resolved = resolver.resolve(json);

    // then
    assertThat(resolved).hasSize(1);
    assertThat(resolved.get(0).isResolved()).isFalse();
    assertThat(resolved.get(0).getErrorMessage()).contains("missing documentId");
    verify(client, never()).newDocumentContentGetRequest(anyString());
  }

  @SuppressWarnings("unchecked")
  private void stubDownload(final String documentId, final byte[] data) {
    final DocumentContentGetRequest perDocRequest =
        org.mockito.Mockito.mock(DocumentContentGetRequest.class);
    lenient().when(perDocRequest.storeId(any())).thenReturn(perDocRequest);
    lenient().when(perDocRequest.contentHash(any())).thenReturn(perDocRequest);
    final CamundaFuture<InputStream> downloadFuture = org.mockito.Mockito.mock(CamundaFuture.class);
    when(downloadFuture.join()).thenReturn(new ByteArrayInputStream(data));
    when(perDocRequest.send()).thenReturn(downloadFuture);
    when(client.newDocumentContentGetRequest(documentId)).thenReturn(perDocRequest);
  }
}

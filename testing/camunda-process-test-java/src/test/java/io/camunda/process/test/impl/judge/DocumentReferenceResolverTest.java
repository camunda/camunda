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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.fetch.DocumentContentGetRequest;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.process.test.api.judge.ResolvedDocument;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.impl.assertions.util.CamundaAssertJsonMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    resolver =
        new DocumentReferenceResolver(
            new CamundaDataSource(client),
            new CamundaAssertJsonMapper(new CamundaObjectMapper(new ObjectMapper())));
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
    when(client.newDocumentContentGetRequest(any(DocumentReferenceResponse.class)))
        .thenReturn(request);
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
    assertThat(doc.getReference().getDocumentId()).isEqualTo("doc-1");
    assertThat(doc.getReference().getStoreId()).isEqualTo("store-1");
    assertThat(doc.getReference().getContentHash()).isEqualTo("hash-1");
    assertThat(doc.getReference().getMetadata().getFileName()).isEqualTo("a.png");
    assertThat(doc.getReference().getMetadata().getContentType()).isEqualTo("image/png");
    assertThat(doc.getContent()).containsExactly(1, 2, 3);

    final ArgumentCaptor<DocumentReferenceResponse> captor =
        ArgumentCaptor.forClass(DocumentReferenceResponse.class);
    verify(client).newDocumentContentGetRequest(captor.capture());
    assertThat(captor.getValue().getDocumentId()).isEqualTo("doc-1");
    assertThat(captor.getValue().getStoreId()).isEqualTo("store-1");
    assertThat(captor.getValue().getContentHash()).isEqualTo("hash-1");
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
        .extracting(d -> d.getReference().getDocumentId())
        .containsExactlyInAnyOrder("doc-a", "doc-b");
  }

  @Test
  void shouldThrowWhenDownloadFails() {
    // given
    when(client.newDocumentContentGetRequest(any(DocumentReferenceResponse.class)))
        .thenReturn(request);
    when(request.send()).thenReturn(future);
    when(future.join()).thenThrow(new RuntimeException("boom"));

    final String json =
        "{\"camunda.document.type\": \"camunda\", \"documentId\": \"doc-x\","
            + " \"metadata\": {\"fileName\": \"x.bin\", \"contentType\": \"application/octet-stream\"}}";

    // when / then
    assertThatThrownBy(() -> resolver.resolve(json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("doc-x")
        .hasMessageContaining("boom");
  }

  @Test
  void shouldThrowForReferenceWithoutDocumentId() {
    // given
    final String json =
        "{\"camunda.document.type\": \"camunda\","
            + " \"metadata\": {\"contentType\": \"image/png\"}}";

    // when / then
    assertThatThrownBy(() -> resolver.resolve(json))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing documentId");
    verify(client, never()).newDocumentContentGetRequest(anyString());
    verify(client, never()).newDocumentContentGetRequest(any(DocumentReferenceResponse.class));
  }

  @SuppressWarnings("unchecked")
  private void stubDownload(final String documentId, final byte[] data) {
    final DocumentContentGetRequest perDocRequest =
        org.mockito.Mockito.mock(DocumentContentGetRequest.class);
    final CamundaFuture<InputStream> downloadFuture = org.mockito.Mockito.mock(CamundaFuture.class);
    when(downloadFuture.join()).thenReturn(new ByteArrayInputStream(data));
    when(perDocRequest.send()).thenReturn(downloadFuture);
    when(client.newDocumentContentGetRequest(
            org.mockito.ArgumentMatchers.<DocumentReferenceResponse>argThat(
                ref -> ref != null && documentId.equals(ref.getDocumentId()))))
        .thenReturn(perDocRequest);
  }
}

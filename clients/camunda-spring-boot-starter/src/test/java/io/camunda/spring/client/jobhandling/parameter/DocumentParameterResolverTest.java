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
package io.camunda.spring.client.jobhandling.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.annotation.value.DocumentValue.ParameterType;
import io.camunda.spring.client.jobhandling.DocumentContext;
import io.camunda.spring.client.jobhandling.DocumentContext.DocumentEntry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class DocumentParameterResolverTest {

  @Nested
  class SingleDocumentReference {
    private List<DocumentReferenceResponse> mockDocumentReferences;

    @BeforeEach
    public void setup() {
      mockDocumentReferences = List.of(mock(DocumentReferenceResponse.class));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentContext(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.CONTEXT, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(DocumentContext.class);
      final DocumentContext documentContext = (DocumentContext) resolved;
      assertThat(documentContext.getDocuments()).hasSize(1);
      final DocumentEntry documentEntry = documentContext.getDocuments().get(0);
      assertThat(documentEntry.getDocumentReference()).isSameAs(mockDocumentReferences.get(0));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentReferenceList(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.LIST, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(List.class);
      final List<DocumentReferenceResponse> documentReferences =
          (List<DocumentReferenceResponse>) resolved;
      assertThat(documentReferences).hasSize(1);
      final DocumentReferenceResponse documentReference = documentReferences.get(0);
      assertThat(documentReference).isEqualTo(mockDocumentReferences.get(0));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentReference(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.SINGLE, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(DocumentReferenceResponse.class);
      final DocumentReferenceResponse documentReference = (DocumentReferenceResponse) resolved;
      assertThat(documentReference).isEqualTo(mockDocumentReferences.get(0));
    }
  }

  @Nested
  class MultipleDocumentReferences {
    private List<DocumentReferenceResponse> mockDocumentReferences;

    @BeforeEach
    public void setup() {
      mockDocumentReferences =
          List.of(mock(DocumentReferenceResponse.class), mock(DocumentReferenceResponse.class));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentContext(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.CONTEXT, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(DocumentContext.class);
      final DocumentContext documentContext = (DocumentContext) resolved;
      assertThat(documentContext.getDocuments()).hasSize(2);
      final DocumentEntry firstDocumentEntry = documentContext.getDocuments().get(0);
      assertThat(firstDocumentEntry.getDocumentReference()).isSameAs(mockDocumentReferences.get(0));
      final DocumentEntry secondDocumentEntry = documentContext.getDocuments().get(1);
      assertThat(secondDocumentEntry.getDocumentReference())
          .isSameAs(mockDocumentReferences.get(1));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentReferenceList(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.LIST, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(List.class);
      final List<DocumentReferenceResponse> documentReferences =
          (List<DocumentReferenceResponse>) resolved;
      assertThat(documentReferences).hasSize(2);
      assertThat(documentReferences.get(0)).isEqualTo(mockDocumentReferences.get(0));
      assertThat(documentReferences.get(1)).isEqualTo(mockDocumentReferences.get(1));
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentReference(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.SINGLE, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(DocumentReferenceResponse.class);
      final DocumentReferenceResponse documentReference = (DocumentReferenceResponse) resolved;
      assertThat(documentReference).isEqualTo(mockDocumentReferences.get(0));
    }
  }

  @Nested
  class EmptyDocumentReferences {
    private List<DocumentReferenceResponse> mockDocumentReferences;

    @BeforeEach
    public void setup() {
      mockDocumentReferences = List.of();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentContext(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.CONTEXT, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(DocumentContext.class);
      final DocumentContext documentContext = (DocumentContext) resolved;
      assertThat(documentContext.getDocuments()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void shouldResolveDocumentReferenceList(final boolean optional) {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", optional, ParameterType.LIST, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(List.class);
      final List<DocumentReferenceResponse> documentReferences =
          (List<DocumentReferenceResponse>) resolved;
      assertThat(documentReferences).isEmpty();
    }

    @Test
    void shouldResolveDocumentReferenceOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", true, ParameterType.SINGLE, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isNull();
    }

    @Test
    void shouldResolveDocumentReferenceNotOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(mockDocumentReferences);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", false, ParameterType.SINGLE, mock(CamundaClient.class));
      assertThatThrownBy(() -> documentParameterResolver.resolve(mock(JobClient.class), job))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Variable foo contains empty list of document references and parameter is not optional");
    }
  }

  @Nested
  class NullDocumentReferences {

    @Test
    void shouldResolveDocumentContextOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(null);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", true, ParameterType.CONTEXT, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(DocumentContext.class);
      final DocumentContext documentContext = (DocumentContext) resolved;
      assertThat(documentContext.getDocuments()).isEmpty();
    }

    @Test
    void shouldResolveDocumentReferenceListOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(null);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver("foo", true, ParameterType.LIST, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isInstanceOf(List.class);
      final List<DocumentReferenceResponse> documentReferences =
          (List<DocumentReferenceResponse>) resolved;
      assertThat(documentReferences).isEmpty();
    }

    @Test
    void shouldResolveDocumentReferenceOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(null);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", true, ParameterType.SINGLE, mock(CamundaClient.class));
      final Object resolved = documentParameterResolver.resolve(mock(JobClient.class), job);
      assertThat(resolved).isNull();
    }

    @Test
    void shouldResolveDocumentReferenceNotOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(null);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", false, ParameterType.SINGLE, mock(CamundaClient.class));
      assertThatThrownBy(() -> documentParameterResolver.resolve(mock(JobClient.class), job))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Could not get document references for variable foo");
    }

    @Test
    void shouldResolveDocumentContextNotOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(null);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", false, ParameterType.CONTEXT, mock(CamundaClient.class));
      assertThatThrownBy(() -> documentParameterResolver.resolve(mock(JobClient.class), job))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Could not get document references for variable foo");
    }

    @Test
    void shouldResolveDocumentReferenceListNotOptional() {
      final ActivatedJob job = mock(ActivatedJob.class);
      when(job.getDocumentReferences(anyString())).thenReturn(null);
      final DocumentParameterResolver documentParameterResolver =
          new DocumentParameterResolver(
              "foo", false, ParameterType.LIST, mock(CamundaClient.class));
      assertThatThrownBy(() -> documentParameterResolver.resolve(mock(JobClient.class), job))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Could not get document references for variable foo");
    }
  }
}

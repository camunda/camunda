/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentError.DocumentHashMismatch;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.gateway.protocol.model.DocumentMetadata;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentContentResponse;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.util.Either;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(DocumentController.class)
public class DocumentControllerTest extends RestControllerTest {

  static final String DOCUMENTS_BASE_URL = "/v2/documents";

  @MockitoBean DocumentServices documentServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(documentServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(documentServices);
  }

  @Test
  void shouldCreateDocumentWithNoMetadata() {
    // given
    final var filename = "file.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};

    final var timestamp = OffsetDateTime.now();

    final ArgumentCaptor<DocumentCreateRequest> requestCaptor =
        ArgumentCaptor.forClass(DocumentCreateRequest.class);
    when(documentServices.createDocument(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DocumentReferenceResponse(
                    "documentId",
                    "default",
                    "dummy_hash",
                    new DocumentMetadataModel(
                        contentType.toString(), filename, timestamp, 0L, null, null, Map.of()))));

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("file", content).contentType(contentType).filename(filename);

    // when/then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            String.format(
                """
                    {
                      "documentId": "documentId",
                      "storeId": "default",
                      "camunda.document.type": "camunda",
                      "contentHash": "dummy_hash",
                      "metadata": {
                        "contentType": "application/octet-stream",
                        "fileName": "file.txt",
                        "expiresAt": "%s",
                        "size": 0,
                        "processDefinitionId": null,
                        "processInstanceKey": null,
                        "customProperties": {}
                      }
                    }
                    """,
                timestamp),
            JsonCompareMode.STRICT);

    verify(documentServices).createDocument(requestCaptor.capture());

    final DocumentCreateRequest request = requestCaptor.getValue();
    assertThat(request.documentId()).isNull();
    assertThat(request.storeId()).isNull();
    assertThat(request.metadata()).isNotNull();
    assertThat(request.contentInputStream()).isNotNull();

    try (final var stream = request.contentInputStream()) {
      assertThat(stream.readAllBytes()).isEqualTo(content);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    final var metadata = request.metadata();
    assertThat(metadata.fileName()).isEqualTo(filename);
    assertThat(metadata.contentType()).isEqualTo(contentType.toString());
  }

  @Test
  void shouldCreateDocumentWithMetadata() {
    // given
    final var filename = "file.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};

    final var timestamp = OffsetDateTime.now();

    final ArgumentCaptor<DocumentCreateRequest> requestCaptor =
        ArgumentCaptor.forClass(DocumentCreateRequest.class);
    when(documentServices.createDocument(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DocumentReferenceResponse(
                    "documentId",
                    "default",
                    "dummy_hash",
                    new DocumentMetadataModel(
                        contentType.toString(), filename, timestamp, 0L, null, null, Map.of()))));

    final var metadataToSend = new DocumentMetadata();
    metadataToSend.setContentType(contentType.toString());
    metadataToSend.setFileName(filename);
    metadataToSend.setExpiresAt(timestamp.toString());

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("file", content).contentType(contentType).filename(filename);
    multipartBodyBuilder.part("metadata", metadataToSend).contentType(MediaType.APPLICATION_JSON);

    // when/then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            String.format(
                """
                    {
                      "documentId": "documentId",
                      "storeId": "default",
                      "camunda.document.type": "camunda",
                      "contentHash": "dummy_hash",
                      "metadata": {
                        "contentType": "application/octet-stream",
                        "fileName": "file.txt",
                        "expiresAt": "%s",
                        "size": 0,
                        "processDefinitionId": null,
                        "processInstanceKey": null,
                        "customProperties": {}
                      }
                    }
                    """,
                timestamp),
            JsonCompareMode.STRICT);

    verify(documentServices).createDocument(requestCaptor.capture());

    final DocumentCreateRequest request = requestCaptor.getValue();
    assertThat(request.documentId()).isNull();
    assertThat(request.storeId()).isNull();
    assertThat(request.metadata()).isNotNull();
    assertThat(request.contentInputStream()).isNotNull();

    try (final var stream = request.contentInputStream()) {
      assertThat(stream.readAllBytes()).isEqualTo(content);
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    final var metadata = request.metadata();
    assertThat(metadata.fileName()).isEqualTo(filename);
    assertThat(metadata.contentType()).isEqualTo(contentType.toString());
  }

  @Test
  void shouldCreateDocumentsBatchWithMetadata() throws JsonProcessingException {
    // given
    final var filename1 = "file.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content1 = new byte[] {1, 2, 3};
    final var filename2 = "file2.txt";
    final var content2 = new byte[] {4, 5};

    final var timestamp = OffsetDateTime.now();

    final ArgumentCaptor<List<DocumentCreateRequest>> requestCaptor =
        ArgumentCaptor.forClass(List.class);
    final var ref1 =
        new DocumentReferenceResponse(
            "documentId",
            "default",
            "dummy_hash",
            new DocumentMetadataModel(
                contentType.toString(), filename1, timestamp, 0L, null, 123L, Map.of()));
    when(documentServices.createDocumentBatch(any()))
        .thenReturn(
            CompletableFuture.completedFuture(List.of(Either.right(ref1), Either.right(ref1))));

    final var om = new ObjectMapper();
    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder
        .part("files", content1)
        .header(
            "X-Document-Metadata",
            om.writeValueAsString(
                new DocumentMetadata()
                    .contentType(contentType.toString())
                    .fileName(filename1)
                    .expiresAt(timestamp.toString())
                    .processInstanceKey("123")));
    multipartBodyBuilder
        .part("files", content2)
        .header(
            "X-Document-Metadata",
            om.writeValueAsString(
                new DocumentMetadata()
                    .contentType(contentType.toString())
                    .fileName(filename2)
                    .expiresAt(timestamp.toString())
                    .processInstanceKey("123")));

    // when/then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL + "/batch")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
                    {
                      "createdDocuments": [
                        {
                          "metadata": {
                            "processInstanceKey": "123",
                            "contentType": "application/octet-stream",
                            "fileName": "file.txt",
                            "expiresAt": "%s",
                            "size": 0,
                            "processDefinitionId": null,
                            "customProperties": {}
                          },
                          "camunda.document.type": "camunda",
                          "storeId": "default",
                          "documentId": "documentId",
                          "contentHash": "dummy_hash"
                        },
                        {
                          "metadata": {
                            "processInstanceKey": "123",
                            "contentType": "application/octet-stream",
                            "fileName": "file.txt",
                            "expiresAt": "%s",
                            "size": 0,
                            "processDefinitionId": null,
                            "customProperties": {}
                          },
                          "camunda.document.type": "camunda",
                          "storeId": "default",
                          "documentId": "documentId",
                          "contentHash": "dummy_hash"
                        }
                      ],
                      "failedDocuments": []
                    }
                    """
                .formatted(timestamp, timestamp),
            JsonCompareMode.STRICT);

    verify(documentServices).createDocumentBatch(requestCaptor.capture());

    final var values = requestCaptor.getValue();
    assertThat(values).extracting(DocumentCreateRequest::documentId).containsOnlyNulls();
    assertThat(values).extracting(DocumentCreateRequest::storeId).containsOnlyNulls();
    assertThat(values).extracting(DocumentCreateRequest::contentInputStream).doesNotContainNull();

    final var metadataValues = values.stream().map(DocumentCreateRequest::metadata).toList();
    assertThat(metadataValues).doesNotContainNull();
    assertThat(metadataValues)
        .extracting(DocumentMetadataModel::fileName)
        .containsExactlyInAnyOrder(filename1, filename2);
    assertThat(metadataValues)
        .extracting(DocumentMetadataModel::contentType)
        .containsExactly(contentType.toString(), contentType.toString());
    assertThat(metadataValues)
        .extracting(DocumentMetadataModel::processInstanceKey)
        .containsExactly(123L, 123L);

    assertThat(values)
        .<InputStream>extracting(DocumentCreateRequest::contentInputStream)
        .map(InputStream::readAllBytes)
        .containsExactlyInAnyOrder(content1, content2);
  }

  @Test
  void testGetDocumentContent() {
    // given
    final var content = new byte[] {1, 2, 3};

    when(documentServices.getDocumentContent("documentId", null, null))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DocumentContentResponse(new ByteArrayInputStream(content), "application/pdf")));

    // when/then
    webClient
        .get()
        .uri(DOCUMENTS_BASE_URL + "/documentId")
        .accept(MediaType.APPLICATION_PDF)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(byte[].class)
        .isEqualTo(content);
  }

  @Test
  void testDeleteDocument() {
    // given
    when(documentServices.deleteDocument("documentId", null))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when/then
    webClient
        .delete()
        .uri(DOCUMENTS_BASE_URL + "/documentId")
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void testNullContentTypeShouldReturnOctetStream() {
    // given
    final var content = new byte[] {1, 2, 3};

    when(documentServices.getDocumentContent("documentId", null, null))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DocumentContentResponse(new ByteArrayInputStream(content), null)));

    // when/then
    webClient
        .get()
        .uri(DOCUMENTS_BASE_URL + "/documentId")
        .accept(MediaType.APPLICATION_OCTET_STREAM)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(byte[].class)
        .isEqualTo(content);
  }

  @Test
  void shouldYieldBadRequestWhenNoHashDocumentForGetDocument() {
    // given
    when(documentServices.getDocumentContent(any(), any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapDocumentError(new DocumentHashMismatch("foo", null))));

    // when/then
    webClient
        .get()
        .uri(DOCUMENTS_BASE_URL + "/foo")
        .accept(MediaType.APPLICATION_OCTET_STREAM)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "No document hash provided for document foo",
                  "instance": "%s"
                }
                """
                .formatted(DOCUMENTS_BASE_URL + "/foo"),
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldYieldBadRequestWhenWrongHashForGetDocument() {
    // given
    when(documentServices.getDocumentContent(any(), any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapDocumentError(new DocumentHashMismatch("foo", "barbaz"))));

    // when/then
    webClient
        .get()
        .uri(DOCUMENTS_BASE_URL + "/foo")
        .accept(MediaType.APPLICATION_OCTET_STREAM)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Document hash for document foo doesn't match the provided hash barbaz",
                  "instance": "%s"
                }
                """
                .formatted(DOCUMENTS_BASE_URL + "/foo"),
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnBadRequestWhenMetadataListLengthMismatch() throws Exception {
    // given two files but only one metadataList entry
    final var filename1 = "file.txt";
    final var filename2 = "file2.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content1 = new byte[] {1, 2, 3};
    final var content2 = new byte[] {4, 5};

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("files", content1).filename(filename1).contentType(contentType);
    multipartBodyBuilder.part("files", content2).filename(filename2).contentType(contentType);

    final var mapper = new ObjectMapper();
    final var meta1 =
        new DocumentMetadata().contentType(contentType.toString()).fileName(filename1);
    // Provide single-element JSON array to simulate mismatch
    multipartBodyBuilder
        .part("metadataList", mapper.writeValueAsString(List.of(meta1)))
        .contentType(MediaType.APPLICATION_JSON);

    // when / then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL + "/batch")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("metadataList length (1) does not match files length (2)");

    // ensure service layer was not invoked
    verify(documentServices, never()).createDocumentBatch(any());
  }

  @Test
  void shouldCreateDocumentsBatchWithMetadataList() throws Exception {
    // given
    final var filename1 = "file.txt";
    final var filename2 = "file2.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content1 = new byte[] {1, 2, 3};
    final var content2 = new byte[] {4, 5};
    final var timestamp = OffsetDateTime.now();

    final ArgumentCaptor<List<DocumentCreateRequest>> requestCaptor =
        ArgumentCaptor.forClass(List.class);
    final var ref =
        new DocumentReferenceResponse(
            "documentId",
            "default",
            "dummy_hash",
            new DocumentMetadataModel(
                contentType.toString(), filename1, timestamp, 0L, null, 123L, Map.of()));
    when(documentServices.createDocumentBatch(any()))
        .thenReturn(
            CompletableFuture.completedFuture(List.of(Either.right(ref), Either.right(ref))));

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("files", content1).filename(filename1).contentType(contentType);
    multipartBodyBuilder.part("files", content2).filename(filename2).contentType(contentType);

    final var mapper = new ObjectMapper();
    final var metadataList =
        List.of(
            new DocumentMetadata()
                .contentType(contentType.toString())
                .fileName(filename1)
                .expiresAt(timestamp.toString())
                .processInstanceKey("123"),
            new DocumentMetadata()
                .contentType(contentType.toString())
                .fileName(filename2)
                .expiresAt(timestamp.toString())
                .processInstanceKey("123"));
    multipartBodyBuilder
        .part("metadataList", mapper.writeValueAsString(metadataList))
        .contentType(MediaType.APPLICATION_JSON);

    // when / then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL + "/batch")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isCreated();

    verify(documentServices).createDocumentBatch(requestCaptor.capture());
    final var values = requestCaptor.getValue();
    assertThat(values).hasSize(2);
    assertThat(values)
        .extracting(d -> d.metadata().processInstanceKey())
        .containsExactly(123L, 123L);
    assertThat(values)
        .extracting(d -> d.metadata().fileName())
        .containsExactlyInAnyOrder(filename1, filename2);
  }

  @Test
  void shouldRejectWhenBothRequestMetadataListAndHeadersProvided() throws Exception {
    // given
    final var filename1 = "file.txt";
    final var filename2 = "file2.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content1 = new byte[] {1, 2, 3};
    final var content2 = new byte[] {4, 5};
    final var timestamp = OffsetDateTime.now();

    final ArgumentCaptor<List<DocumentCreateRequest>> requestCaptor =
        ArgumentCaptor.forClass(List.class);
    final var ref =
        new DocumentReferenceResponse(
            "documentId",
            "default",
            "dummy_hash",
            new DocumentMetadataModel(
                contentType.toString(), filename1, timestamp, 0L, null, 123L, Map.of()));
    // Should not be invoked due to validation failure

    final var mapper = new ObjectMapper();
    final var multipartBodyBuilder = new MultipartBodyBuilder();

    // Files with conflicting header metadata (processInstanceKey 999, different fileName hint)
    multipartBodyBuilder
        .part("files", content1)
        .filename(filename1)
        .contentType(contentType)
        .header(
            "X-Document-Metadata",
            mapper.writeValueAsString(
                new DocumentMetadata()
                    .contentType(contentType.toString())
                    .fileName("IGNORED-" + filename1)
                    .processInstanceKey("999")));
    multipartBodyBuilder
        .part("files", content2)
        .filename(filename2)
        .contentType(contentType)
        .header(
            "X-Document-Metadata",
            mapper.writeValueAsString(
                new DocumentMetadata()
                    .contentType(contentType.toString())
                    .fileName("IGNORED-" + filename2)
                    .processInstanceKey("999")));

    // Preferred metadataList (processInstanceKey 123, original file names)
    final var metadataList =
        List.of(
            new DocumentMetadata()
                .contentType(contentType.toString())
                .fileName(filename1)
                .processInstanceKey("123"),
            new DocumentMetadata()
                .contentType(contentType.toString())
                .fileName(filename2)
                .processInstanceKey("123"));
    multipartBodyBuilder
        .part("metadataList", mapper.writeValueAsString(metadataList))
        .contentType(MediaType.APPLICATION_JSON);

    // when / then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL + "/batch")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("Specify either metadataList part or X-Document-Metadata headers, but not both");

    verify(documentServices, never()).createDocumentBatch(any());
  }
}

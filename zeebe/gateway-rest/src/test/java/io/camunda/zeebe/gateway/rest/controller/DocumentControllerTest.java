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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentContentResponse;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.protocol.rest.DocumentDetails;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.rest.RequestMapper;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;

@WebMvcTest(DocumentController.class)
public class DocumentControllerTest extends RestControllerTest {

  static final String DOCUMENTS_BASE_URL = "/v2/documents";

  @MockBean DocumentServices documentServices;

  @BeforeEach
  void setUp() {
    when(documentServices.withAuthentication(any(Authentication.class)))
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
                      "metadata": {
                        "contentType": "application/octet-stream",
                        "fileName": "file.txt",
                        "expiresAt": "%s"
                      }
                    }
                    """,
                timestamp));

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
  void shouldCreateDocumentWithMetadataNumberKeys() {
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
                        contentType.toString(), filename, timestamp, 0L, null, 123L, Map.of()))));

    final var metadataToSend = new DocumentDetails();
    metadataToSend.setContentType(contentType.toString());
    metadataToSend.setFileName(filename);
    metadataToSend.setExpiresAt(timestamp.toString());
    metadataToSend.setProcessInstanceKey(123L);

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("file", content).contentType(contentType).filename(filename);
    multipartBodyBuilder.part("metadata", metadataToSend).contentType(MediaType.APPLICATION_JSON);

    // when/then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(RequestMapper.MEDIA_TYPE_KEYS_NUMBER)
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
                      "metadata": {
                        "contentType": "application/octet-stream",
                        "fileName": "file.txt",
                        "expiresAt": "%s",
                        "processInstanceKey": 123
                      }
                    }
                    """,
                timestamp));

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
    assertThat(metadata.processInstanceKey()).isEqualTo(123L);
  }

  @Test
  void shouldCreateDocumentWithMetadataStringKeys() {
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
                        contentType.toString(), filename, timestamp, 0L, null, 123L, Map.of()))));

    final var metadataToSend = new DocumentMetadata();
    metadataToSend.setContentType(contentType.toString());
    metadataToSend.setFileName(filename);
    metadataToSend.setExpiresAt(timestamp.toString());
    metadataToSend.setProcessInstanceKey("123");

    final var multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("file", content).contentType(contentType).filename(filename);
    multipartBodyBuilder.part("metadata", metadataToSend).contentType(MediaType.APPLICATION_JSON);

    // when/then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(RequestMapper.MEDIA_TYPE_KEYS_STRING)
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
                      "metadata": {
                        "contentType": "application/octet-stream",
                        "fileName": "file.txt",
                        "expiresAt": "%s",
                        "processInstanceKey": "123"
                      }
                    }
                    """,
                timestamp));

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
    assertThat(metadata.processInstanceKey()).isEqualTo(123L);
  }

  @Test
  void shouldCreateDocumentsBatchWithMetadataNumberKeys() throws JsonProcessingException {
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
                new DocumentDetails()
                    .contentType(contentType.toString())
                    .fileName(filename1)
                    .expiresAt(timestamp.toString())
                    .processInstanceKey(123L)));
    multipartBodyBuilder
        .part("files", content2)
        .header(
            "X-Document-Metadata",
            om.writeValueAsString(
                new DocumentDetails()
                    .contentType(contentType.toString())
                    .fileName(filename2)
                    .expiresAt(timestamp.toString())
                    .processInstanceKey(123L)));

    // when/then
    webClient
        .post()
        .uri(DOCUMENTS_BASE_URL + "/batch")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .bodyValue(multipartBodyBuilder.build())
        .accept(RequestMapper.MEDIA_TYPE_KEYS_NUMBER)
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
                            "processInstanceKey": 123,
                            "contentType": "application/octet-stream",
                            "fileName": "file.txt",
                            "expiresAt": "%s",
                            "size": 0,
                            "customProperties": {}
                          },
                          "camunda.document.type": "camunda",
                          "storeId": "default",
                          "documentId": "documentId",
                          "contentHash": "dummy_hash"
                        },
                        {
                          "metadata": {
                            "processInstanceKey": 123,
                            "contentType": "application/octet-stream",
                            "fileName": "file.txt",
                            "expiresAt": "%s",
                            "size": 0,
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
                .formatted(timestamp, timestamp));

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
  void shouldCreateDocumentsBatchWithMetadataStringKeys() throws JsonProcessingException {
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
        .accept(RequestMapper.MEDIA_TYPE_KEYS_STRING)
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
                .formatted(timestamp, timestamp));

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
            new DocumentContentResponse(new ByteArrayInputStream(content), "application/pdf"));

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
        .thenReturn(new DocumentContentResponse(new ByteArrayInputStream(content), null));

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

  // TODO: test error cases
}

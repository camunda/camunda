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

import io.camunda.document.api.DocumentMetadataModel;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentCreateRequest;
import io.camunda.service.DocumentServices.DocumentReferenceResponse;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
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

    final var timestamp = ZonedDateTime.now();

    final ArgumentCaptor<DocumentCreateRequest> requestCaptor =
        ArgumentCaptor.forClass(DocumentCreateRequest.class);
    when(documentServices.createDocument(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DocumentReferenceResponse(
                    "documentId",
                    "default",
                    new DocumentMetadataModel(
                        contentType.toString(), filename, timestamp, 0L, Map.of()))));

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
  void shouldCreateDocumentWithMetadata() {
    // given
    final var filename = "file.txt";
    final var contentType = MediaType.APPLICATION_OCTET_STREAM;
    final var content = new byte[] {1, 2, 3};

    final var timestamp = ZonedDateTime.now();

    final ArgumentCaptor<DocumentCreateRequest> requestCaptor =
        ArgumentCaptor.forClass(DocumentCreateRequest.class);
    when(documentServices.createDocument(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new DocumentReferenceResponse(
                    "documentId",
                    "default",
                    new DocumentMetadataModel(
                        contentType.toString(), filename, timestamp, 0L, Map.of()))));

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
  void testGetDocumentContent() {
    // given
    final var content = new byte[] {1, 2, 3};

    when(documentServices.getDocumentContent("documentId", null))
        .thenReturn(new ByteArrayInputStream(content));

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

  // TODO: test error cases
}

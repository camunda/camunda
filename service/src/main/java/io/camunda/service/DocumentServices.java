/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DocumentServices extends ApiServices<DocumentServices> {

  // TODO: This is an in-memory implementation, replace it with a real document store
  private static final String STORE_ID = "default";
  private final Map<String, byte[]> documents;

  public DocumentServices(final BrokerClient brokerClient, final CamundaSearchClient searchClient) {
    this(brokerClient, searchClient, null, null, new HashMap<>());
  }

  public DocumentServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication,
      final Map<String, byte[]> documents) {
    super(brokerClient, searchClient, transformers, authentication);
    this.documents = documents;
  }

  @Override
  public DocumentServices withAuthentication(final Authentication authentication) {
    return new DocumentServices(
        brokerClient, searchClient, transformers, authentication, documents);
  }

  public CompletableFuture<DocumentReferenceResponse> createDocument(
      DocumentCreateRequest request) {
    validateStoreId(request.storeId);
    final String id =
        request.documentId != null ? request.documentId : UUID.randomUUID().toString();
    if (documents.containsKey(id)) {
      throw new CamundaServiceException("Document already exists: " + id);
    }
    final var contentInputStream = request.contentInputStream;
    final byte[] content;
    try {
      content = contentInputStream.readAllBytes();
      contentInputStream.close();
    } catch (IOException e) {
      throw new CamundaServiceException("Failed to read document content", e);
    }
    documents.put(id, content);
    return CompletableFuture.completedFuture(
        new DocumentReferenceResponse(id, STORE_ID, request.metadata));
  }

  public InputStream getDocumentContent(String documentId, String storeId) {
    validateStoreId(storeId);
    final var content = documents.get(documentId);
    if (content == null) {
      throw new CamundaServiceException("Document not found: " + documentId);
    }
    return new ByteArrayInputStream(content);
  }

  public CompletableFuture<Void> deleteDocument(String documentId, String storeId) {
    validateStoreId(storeId);
    final var content = documents.remove(documentId);
    if (content == null) {
      throw new CamundaServiceException("Document not found: " + documentId);
    }
    return CompletableFuture.completedFuture(null);
  }

  private void validateStoreId(String storeId) {
    if (storeId != null && !storeId.equals(STORE_ID)) {
      throw new CamundaServiceException(
          "Unsupported store id: " + storeId + ", expected: " + STORE_ID);
    }
  }

  public record DocumentCreateRequest(
      String documentId,
      String storeId,
      InputStream contentInputStream,
      DocumentMetadataModel metadata) {}

  public record DocumentMetadataModel(
      String contentType,
      String fileName,
      ZonedDateTime expiresAt,
      Map<String, Object> additionalProperties) {}

  public record DocumentReferenceResponse(
      String documentId, String storeId, DocumentMetadataModel metadata) {}
}

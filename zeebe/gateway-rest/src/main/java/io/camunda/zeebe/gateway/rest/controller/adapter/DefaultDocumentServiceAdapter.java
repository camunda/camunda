/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentLinkRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.DocumentMetadataContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.DocumentServices;
import io.camunda.zeebe.gateway.rest.controller.generated.DocumentServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.http.Part;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Component
public class DefaultDocumentServiceAdapter implements DocumentServiceAdapter {

  private final DocumentServices documentServices;
  private final ObjectMapper objectMapper;

  public DefaultDocumentServiceAdapter(
      final DocumentServices documentServices, final ObjectMapper objectMapper) {
    this.documentServices = documentServices;
    this.objectMapper = objectMapper;
  }

  @Override
  public ResponseEntity<Object> createDocument(
      final String storeId,
      final String documentId,
      final Part file,
      final DocumentMetadataContract metadataStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toDocumentCreateRequest(documentId, storeId, file, metadataStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> documentServices.createDocument(request, authentication),
                    ResponseMapper::toDocumentReference,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> createDocuments(
      final String storeId,
      final List<Part> files,
      final List<DocumentMetadataContract> metadataListStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toDocumentCreateRequestBatch(
            files, storeId, objectMapper, metadataListStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            requests ->
                RequestExecutor.executeSync(
                    () -> documentServices.createDocumentBatch(requests, authentication),
                    ResponseMapper::toDocumentReferenceBatch,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Void> deleteDocument(
      final String documentId, final String storeId, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> documentServices.deleteDocument(documentId, storeId, authentication));
  }

  @Override
  public ResponseEntity<StreamingResponseBody> getDocument(
      final String documentId,
      final String storeId,
      final String contentHash,
      final CamundaAuthentication authentication) {
    final var contentResponse =
        documentServices
            .getDocumentContent(documentId, storeId, contentHash, authentication)
            .join();
    final var mediaType = ResponseMapper.resolveMediaType(contentResponse);
    final StreamingResponseBody streamingBody =
        bodyStream -> {
          try (final var contentInputStream = contentResponse.content()) {
            contentInputStream.transferTo(bodyStream);
          }
        };
    return ResponseEntity.ok().contentType(mediaType).body(streamingBody);
  }

  @Override
  public ResponseEntity<Object> createDocumentLink(
      final String documentId,
      final String storeId,
      final String contentHash,
      final DocumentLinkRequestContract linkRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toDocumentLinkParams(linkRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            params ->
                RequestExecutor.executeSync(
                    () ->
                        documentServices.createLink(
                            documentId, storeId, contentHash, params, authentication),
                    ResponseMapper::toDocumentLinkResponse,
                    HttpStatus.OK));
  }
}

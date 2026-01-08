/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.DocumentLinkRequest;
import io.camunda.gateway.protocol.model.DocumentMetadata;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentContentResponse;
import io.camunda.service.DocumentServices.DocumentLinkParams;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.http.Part;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@CamundaRestController
@RequestMapping("/v2/documents")
public class DocumentController {

  private final DocumentServices documentServices;
  private final ObjectMapper objectMapper;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DocumentController(
      final DocumentServices documentServices,
      final ObjectMapper objectMapper,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.documentServices = documentServices;
    this.objectMapper = objectMapper;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createDocument(
      @RequestParam(required = false) final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestPart(value = "file") final Part file,
      @RequestPart(value = "metadata", required = false) final DocumentMetadata metadata) {

    return RequestMapper.toDocumentCreateRequest(documentId, storeId, file, metadata)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createDocument);
  }

  @CamundaPostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createDocuments(
      @RequestPart(value = "files") final List<Part> files,
      @RequestPart(value = "metadataList", required = false)
          final List<DocumentMetadata> metadataList,
      @RequestParam(required = false) final String storeId) {
    // Pass metadataList to let mapper prefer it over legacy headers when provided
    return RequestMapper.toDocumentCreateRequestBatch(files, storeId, objectMapper, metadataList)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createDocumentBatch);
  }

  private CompletableFuture<ResponseEntity<Object>> createDocument(
      final DocumentServices.DocumentCreateRequest request) {

    return RequestExecutor.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createDocument(request),
        ResponseMapper::toDocumentReference,
        HttpStatus.CREATED);
  }

  private CompletableFuture<ResponseEntity<Object>> createDocumentBatch(
      final List<DocumentServices.DocumentCreateRequest> requests) {

    return RequestExecutor.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createDocumentBatch(requests),
        ResponseMapper::toDocumentReferenceBatch,
        response ->
            response.getFailedDocuments().isEmpty() ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS);
  }

  @CamundaGetMapping(
      path = "/{documentId}",
      produces = {}) // produces arbitrary content type
  public ResponseEntity<StreamingResponseBody> getDocumentContent(
      @PathVariable final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestParam(required = false) final String contentHash) {

    // handle the future explicitly here because a StreamingResponseBody is needed as result instead
    // of a future wrapping the stream response
    return documentServices
        .withAuthentication(authenticationProvider.getCamundaAuthentication())
        .getDocumentContent(documentId, storeId, contentHash)
        // Any service exception that can occur is handled by the GlobalControllerExceptionHandler
        .thenApply(DocumentController::toDocumentContentResponse)
        .join();
  }

  private static ResponseEntity<StreamingResponseBody> toDocumentContentResponse(
      final DocumentContentResponse response) {
    final MediaType mediaType = ResponseMapper.resolveMediaType(response);
    return ResponseEntity.ok()
        .contentType(mediaType)
        .body(
            bodyStream -> {
              try (final var contentInputStream = response.content()) {
                contentInputStream.transferTo(bodyStream);
              }
            });
  }

  @CamundaDeleteMapping(path = "/{documentId}")
  public CompletableFuture<ResponseEntity<Object>> deleteDocument(
      @PathVariable final String documentId, @RequestParam(required = false) final String storeId) {

    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteDocument(documentId, storeId));
  }

  @CamundaPostMapping(path = "/{documentId}/links")
  public CompletableFuture<ResponseEntity<Object>> createDocumentLink(
      @PathVariable final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestParam(required = false) final String contentHash,
      @RequestBody final DocumentLinkRequest linkRequest) {

    return RequestMapper.toDocumentLinkParams(linkRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            params -> createDocumentLink(documentId, storeId, contentHash, params));
  }

  private CompletableFuture<ResponseEntity<Object>> createDocumentLink(
      final String documentId,
      final String storeId,
      final String contentHash,
      final DocumentLinkParams params) {

    return RequestExecutor.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createLink(documentId, storeId, contentHash, params),
        ResponseMapper::toDocumentLinkResponse,
        HttpStatus.OK);
  }
}

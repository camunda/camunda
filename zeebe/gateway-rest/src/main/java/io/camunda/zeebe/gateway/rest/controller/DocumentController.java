/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentLinkParams;
import io.camunda.zeebe.gateway.protocol.rest.DocumentLinkRequest;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaDeleteMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import jakarta.servlet.http.Part;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
      @RequestParam(required = false) final String storeId) {

    return RequestMapper.toDocumentCreateRequestBatch(files, storeId, objectMapper)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createDocumentBatch);
  }

  private CompletableFuture<ResponseEntity<Object>> createDocument(
      final DocumentServices.DocumentCreateRequest request) {

    return RequestMapper.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createDocument(request),
        ResponseMapper::toDocumentReference);
  }

  private CompletableFuture<ResponseEntity<Object>> createDocumentBatch(
      final List<DocumentServices.DocumentCreateRequest> requests) {

    return RequestMapper.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createDocumentBatch(requests),
        ResponseMapper::toDocumentReferenceBatch);
  }

  @CamundaGetMapping(
      path = "/{documentId}",
      produces = {}) // produces arbitrary content type
  public ResponseEntity<StreamingResponseBody> getDocumentContent(
      @PathVariable final String documentId, @RequestParam(required = false) final String storeId) {

    // handle the future explicitly here because a StreamingResponseBody is needed as result instead
    // of a future wrapping the stream response
    return documentServices
        .withAuthentication(authenticationProvider.getCamundaAuthentication())
        .getDocumentContent(documentId, storeId)
        // Any service exception that can occur is handled by the GlobalControllerExceptionHandler
        .thenApply(ResponseMapper::toDocumentContentResponse)
        .join();
  }

  @CamundaDeleteMapping(path = "/{documentId}")
  public CompletableFuture<ResponseEntity<Object>> deleteDocument(
      @PathVariable final String documentId, @RequestParam(required = false) final String storeId) {

    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .deleteDocument(documentId, storeId));
  }

  @CamundaPostMapping(path = "/{documentId}/links")
  public CompletableFuture<ResponseEntity<Object>> createDocumentLink(
      @PathVariable final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestBody final DocumentLinkRequest linkRequest) {

    return RequestMapper.toDocumentLinkParams(linkRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            params -> createDocumentLink(documentId, storeId, params));
  }

  private CompletableFuture<ResponseEntity<Object>> createDocumentLink(
      final String documentId, final String storeId, final DocumentLinkParams params) {

    return RequestMapper.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .createLink(documentId, storeId, params),
        ResponseMapper::toDocumentLinkResponse);
  }
}

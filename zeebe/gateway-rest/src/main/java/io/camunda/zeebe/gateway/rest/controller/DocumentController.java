/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentContentResponse;
import io.camunda.service.DocumentServices.DocumentException;
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
import java.util.concurrent.CompletionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

  public DocumentController(
      final DocumentServices documentServices,
      @Qualifier("gatewayRestObjectMapper") final ObjectMapper objectMapper) {
    this.documentServices = documentServices;
    this.objectMapper = objectMapper;
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
                .withAuthentication(RequestMapper.getAuthentication())
                .createDocument(request),
        ResponseMapper::toDocumentReference);
  }

  private CompletableFuture<ResponseEntity<Object>> createDocumentBatch(
      final List<DocumentServices.DocumentCreateRequest> requests) {

    return RequestMapper.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createDocumentBatch(requests),
        ResponseMapper::toDocumentReferenceBatch);
  }

  @CamundaGetMapping(
      path = "/{documentId}",
      produces = {}) // produces arbitrary content type
  public ResponseEntity<StreamingResponseBody> getDocumentContent(
      @PathVariable final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestParam(required = false) final String contentHash) {

    try {
      final DocumentContentResponse contentResponse =
          getDocumentContentResponse(documentId, storeId, contentHash);
      final MediaType mediaType = resolveMediaType(contentResponse);
      return ResponseEntity.ok()
          .contentType(mediaType)
          .body(
              bodyStream -> {
                try (final var contentInputStream = contentResponse.content()) {
                  contentInputStream.transferTo(bodyStream);
                }
              });
    } catch (final Exception e) {
      // we can't return a generic Object type when streaming a response due to Spring MVC
      // limitations
      // exception handling is done in the exception handler below
      if (e instanceof final CompletionException ce) {
        throw new DocumentContentFetchException("Failed to get document content", ce.getCause());
      }
      throw new DocumentContentFetchException("Failed to get document content", e);
    }
  }

  private MediaType resolveMediaType(final DocumentContentResponse contentResponse) {
    try {
      final var contentType = contentResponse.contentType();
      if (contentType == null) {
        return MediaType.APPLICATION_OCTET_STREAM;
      }
      return MediaType.parseMediaType(contentResponse.contentType());
    } catch (final InvalidMediaTypeException e) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }

  @ExceptionHandler(DocumentContentFetchException.class)
  public ResponseEntity<Object> handleDocumentContentException(
      final DocumentContentFetchException e) {
    if (e.getCause() instanceof final DocumentException de) {
      return RestErrorMapper.mapDocumentHandlingExceptionToResponse(de);
    } else {
      return RestErrorMapper.mapProblemToResponse(
          RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e.getClass().getName()));
    }
  }

  private DocumentContentResponse getDocumentContentResponse(
      final String documentId, final String storeId, final String contentHash) {
    return documentServices
        .withAuthentication(RequestMapper.getAuthentication())
        .getDocumentContent(documentId, storeId, contentHash);
  }

  @CamundaDeleteMapping(path = "/{documentId}")
  public CompletableFuture<ResponseEntity<Object>> deleteDocument(
      @PathVariable final String documentId, @RequestParam(required = false) final String storeId) {

    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            documentServices
                .withAuthentication(RequestMapper.getAuthentication())
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

    return RequestMapper.executeServiceMethod(
        () ->
            documentServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createLink(documentId, storeId, contentHash, params),
        ResponseMapper::toDocumentLinkResponse);
  }

  public static class DocumentContentFetchException extends RuntimeException {

    public DocumentContentFetchException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}

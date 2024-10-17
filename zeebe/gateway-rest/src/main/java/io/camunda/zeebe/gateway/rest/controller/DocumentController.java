/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.DocumentServices;
import io.camunda.service.DocumentServices.DocumentException;
import io.camunda.zeebe.gateway.protocol.rest.DocumentLinkRequest;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@CamundaRestController
@RequestMapping("/v2/documents")
public class DocumentController {

  private final DocumentServices documentServices;

  public DocumentController(final DocumentServices documentServices) {
    this.documentServices = documentServices;
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> createDocument(
      @RequestParam(required = false) final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestPart("file") final MultipartFile file,
      @RequestPart(value = "metadata", required = false) final DocumentMetadata metadata) {

    return RequestMapper.toDocumentCreateRequest(documentId, storeId, file, metadata)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createDocument);
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

  @GetMapping(
      path = "/{documentId}",
      produces = {
        MediaType.APPLICATION_OCTET_STREAM_VALUE,
        MediaType.APPLICATION_PROBLEM_JSON_VALUE
      })
  public ResponseEntity<StreamingResponseBody> getDocumentContent(
      @PathVariable final String documentId, @RequestParam(required = false) final String storeId) {

    try {
      final InputStream contentInputStream = getDocumentContentStream(documentId, storeId);
      return ResponseEntity.ok().body(contentInputStream::transferTo);
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

  private InputStream getDocumentContentStream(final String documentId, final String storeId) {
    return documentServices
        .withAuthentication(RequestMapper.getAuthentication())
        .getDocumentContent(documentId, storeId);
  }

  @DeleteMapping(
      path = "/{documentId}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deleteDocument(
      @PathVariable final String documentId, @RequestParam(required = false) final String storeId) {

    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            documentServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteDocument(documentId, storeId));
  }

  @PostMapping(
      path = "/{documentId}/links",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public ResponseEntity<Object> createDocumentLink(
      @PathVariable final String documentId,
      @RequestParam(required = false) final String storeId,
      @RequestBody final DocumentLinkRequest linkRequest) {

    return RequestMapper.toDocumentLinkParams(linkRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            params ->
                documentServices
                    .withAuthentication(RequestMapper.getAuthentication())
                    .createLink(documentId, storeId, params)
                    .thenApply(ResponseMapper::toDocumentLinkResponse)
                    .join());
  }

  public static class DocumentContentFetchException extends RuntimeException {

    public DocumentContentFetchException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}

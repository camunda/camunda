/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.service.CamundaServiceException;
import io.camunda.service.DocumentServices;
import io.camunda.zeebe.gateway.protocol.rest.DocumentLinkRequest;
import io.camunda.zeebe.gateway.protocol.rest.DocumentMetadata;
import io.camunda.zeebe.gateway.protocol.rest.ProblemDetail;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Autowired
  public DocumentController(final DocumentServices documentServices) {
    this.documentServices = documentServices;
  }

  @PostMapping(
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> createDocument(
      @RequestParam(required = false) String documentId,
      @RequestParam(required = false) String storeId,
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metadata", required = false) DocumentMetadata metadata) {

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
      @PathVariable String documentId, @RequestParam(required = false) String storeId)
      throws IOException {

    try (final InputStream contentInputStream = getDocumentContentStream(documentId, storeId)) {
      return ResponseEntity.ok().body(contentInputStream::transferTo);
    }
  }

  @ExceptionHandler(CamundaServiceException.class)
  public ResponseEntity<ProblemDetail> handleDocumentContentException(CamundaServiceException e) {
    final var problemDetail =
        RestErrorMapper.createProblemDetail(
            HttpStatus.BAD_REQUEST, e.getMessage(), "Failed to get document content");
    return RestErrorMapper.mapProblemToResponse(problemDetail);
  }

  private InputStream getDocumentContentStream(String documentId, String storeId) {
    return documentServices
        .withAuthentication(RequestMapper.getAuthentication())
        .getDocumentContent(documentId, storeId);
  }

  @DeleteMapping(
      path = "/{documentId}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deleteDocument(
      @PathVariable String documentId, @RequestParam(required = false) String storeId) {

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
  public ResponseEntity<Void> createDocumentLink(
      @PathVariable String documentId, @RequestBody DocumentLinkRequest linkRequest) {

    // TODO: implement
    final var problemDetail =
        RestErrorMapper.createProblemDetail(
            HttpStatus.NOT_IMPLEMENTED, "Not implemented", "Not implemented");
    return RestErrorMapper.mapProblemToResponse(problemDetail);
  }
}

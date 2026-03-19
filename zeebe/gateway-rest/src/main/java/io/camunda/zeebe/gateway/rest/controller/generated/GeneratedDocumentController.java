/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentLinkRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDocumentMetadataStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import jakarta.servlet.http.Part;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedDocumentController {

  private final DocumentServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedDocumentController(
      final DocumentServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/documents",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createDocument(
      @RequestParam(name = "storeId", required = false) final String storeId,
      @RequestParam(name = "documentId", required = false) final String documentId,
      @RequestPart("file") final Part file,
      @RequestPart(value = "metadata", required = false)
          final GeneratedDocumentMetadataStrictContract metadata) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.createDocument(storeId, documentId, file, metadata, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/documents/batch",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createDocuments(
      @RequestParam(name = "storeId", required = false) final String storeId,
      @RequestPart("files") final List<Part> files,
      @RequestPart(value = "metadataList", required = false)
          final List<GeneratedDocumentMetadataStrictContract> metadataList) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.createDocuments(storeId, files, metadataList, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/documents/{documentId}",
      produces = {})
  public ResponseEntity<StreamingResponseBody> getDocument(
      @PathVariable("documentId") final String documentId,
      @RequestParam(name = "storeId", required = false) final String storeId,
      @RequestParam(name = "contentHash", required = false) final String contentHash) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.getDocument(documentId, storeId, contentHash, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/documents/{documentId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteDocument(
      @PathVariable("documentId") final String documentId,
      @RequestParam(name = "storeId", required = false) final String storeId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.deleteDocument(documentId, storeId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/documents/{documentId}/links",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createDocumentLink(
      @PathVariable("documentId") final String documentId,
      @RequestParam(name = "storeId", required = false) final String storeId,
      @RequestParam(name = "contentHash", required = false) final String contentHash,
      @RequestBody(required = false)
          final GeneratedDocumentLinkRequestStrictContract documentLinkRequest) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return serviceAdapter.createDocumentLink(
        documentId, storeId, contentHash, documentLinkRequest, authentication);
  }
}

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
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import jakarta.servlet.http.Part;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Service adapter for Document operations. Implements request mapping, service delegation, and
 * response construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface DocumentServiceAdapter {

  ResponseEntity<Object> createDocument(
      String storeId,
      String documentId,
      Part file,
      GeneratedDocumentMetadataStrictContract metadata,
      CamundaAuthentication authentication);

  ResponseEntity<Object> createDocuments(
      String storeId,
      List<Part> files,
      List<GeneratedDocumentMetadataStrictContract> metadataList,
      CamundaAuthentication authentication);

  ResponseEntity<StreamingResponseBody> getDocument(
      String documentId, String storeId, String contentHash, CamundaAuthentication authentication);

  ResponseEntity<Void> deleteDocument(
      String documentId, String storeId, CamundaAuthentication authentication);

  ResponseEntity<Object> createDocumentLink(
      String documentId,
      String storeId,
      String contentHash,
      GeneratedDocumentLinkRequestStrictContract documentLinkRequest,
      CamundaAuthentication authentication);
}

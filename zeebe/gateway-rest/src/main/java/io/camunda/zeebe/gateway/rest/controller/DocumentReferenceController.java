/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.DocumentReferenceSearchQuery;
import io.camunda.search.query.DocumentReferenceQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.DocumentReferenceServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/document-references")
public class DocumentReferenceController {

  private final DocumentReferenceServices documentReferenceServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public DocumentReferenceController(
      final DocumentReferenceServices documentReferenceServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.documentReferenceServices = documentReferenceServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchDocumentReferences(
      @RequestBody(required = false) final DocumentReferenceSearchQuery query) {
    return SearchQueryRequestMapper.toDocumentReferenceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private ResponseEntity<Object> search(final DocumentReferenceQuery query) {
    try {
      final var result =
          documentReferenceServices.search(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDocumentReferenceSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

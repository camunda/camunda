/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.auditlog;

import io.camunda.search.query.AuditLogQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.AuditLogServices;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogResult;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogSearchQueryRequest;
import io.camunda.zeebe.gateway.protocol.rest.AuditLogSearchQueryResult;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.mapper.search.SearchQueryResponseMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/audit-logs")
public class AuditLogController {

  private final AuditLogServices auditLogServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public AuditLogController(
      final AuditLogServices auditLogServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.auditLogServices = auditLogServices;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<AuditLogSearchQueryResult> searchAuditLogs(
      @RequestBody(required = false) final AuditLogSearchQueryRequest query) {
    return SearchQueryRequestMapper.toAuditLogQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{auditLogKey}")
  public ResponseEntity<AuditLogResult> getAuditLog(@PathVariable final String auditLogKey) {
    try {
      return ResponseEntity.ok()
          .body(
              SearchQueryResponseMapper.toAuditLog(
                  auditLogServices
                      .withAuthentication(authenticationProvider.getCamundaAuthentication())
                      .getAuditLog(auditLogKey)));
    } catch (final Exception exception) {
      return RestErrorMapper.mapErrorToResponse(exception);
    }
  }

  private ResponseEntity<AuditLogSearchQueryResult> search(final AuditLogQuery query) {
    try {
      final var result =
          auditLogServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }
}

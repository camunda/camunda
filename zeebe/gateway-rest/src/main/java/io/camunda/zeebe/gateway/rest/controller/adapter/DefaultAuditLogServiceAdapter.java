/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAuditLogSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuditLogServices;
import io.camunda.zeebe.gateway.rest.controller.generated.AuditLogServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultAuditLogServiceAdapter implements AuditLogServiceAdapter {

  private final AuditLogServices auditLogServices;

  public DefaultAuditLogServiceAdapter(final AuditLogServices auditLogServices) {
    this.auditLogServices = auditLogServices;
  }

  @Override
  public ResponseEntity<Object> searchAuditLogs(
      final GeneratedAuditLogSearchQueryRequestStrictContract auditLogSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toAuditLogQueryStrict(auditLogSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = auditLogServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toAuditLogSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getAuditLog(
      final Long auditLogKey, final CamundaAuthentication authentication) {
    try {
      final var result = auditLogServices.getAuditLog(String.valueOf(auditLogKey), authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toAuditLog(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

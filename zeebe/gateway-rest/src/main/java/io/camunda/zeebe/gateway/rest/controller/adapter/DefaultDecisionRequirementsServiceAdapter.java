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
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionRequirementsSearchQueryRequestStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.DecisionRequirementsServices;
import io.camunda.zeebe.gateway.rest.controller.generated.DecisionRequirementsServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultDecisionRequirementsServiceAdapter
    implements DecisionRequirementsServiceAdapter {

  private final DecisionRequirementsServices decisionRequirementsServices;

  public DefaultDecisionRequirementsServiceAdapter(
      final DecisionRequirementsServices decisionRequirementsServices) {
    this.decisionRequirementsServices = decisionRequirementsServices;
  }

  @Override
  public ResponseEntity<Object> searchDecisionRequirements(
      final GeneratedDecisionRequirementsSearchQueryRequestStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toDecisionRequirementsQueryStrict(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> {
              try {
                final var result = decisionRequirementsServices.search(q, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toDecisionRequirementsSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getDecisionRequirements(
      final Long decisionRequirementsKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionRequirements(
              decisionRequirementsServices.getByKey(decisionRequirementsKey, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResponseEntity<Void> getDecisionRequirementsXML(
      final Long decisionRequirementsKey, final CamundaAuthentication authentication) {
    try {
      return (ResponseEntity)
          ResponseEntity.ok()
              .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
              .body(
                  decisionRequirementsServices.getDecisionRequirementsXml(
                      decisionRequirementsKey, authentication));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

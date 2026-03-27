/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionDefinitionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionEvaluationInstructionStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.DecisionDefinitionServices;
import io.camunda.zeebe.gateway.rest.controller.generated.DecisionDefinitionServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultDecisionDefinitionServiceAdapter implements DecisionDefinitionServiceAdapter {

  private final DecisionDefinitionServices decisionDefinitionServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultDecisionDefinitionServiceAdapter(
      final DecisionDefinitionServices decisionDefinitionServices,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.decisionDefinitionServices = decisionDefinitionServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  public ResponseEntity<Object> searchDecisionDefinitions(
      final GeneratedDecisionDefinitionSearchQueryRequestStrictContract queryStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toDecisionDefinitionQueryStrict(queryStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> {
              try {
                final var result = decisionDefinitionServices.search(q, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toDecisionDefinitionSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getDecisionDefinition(
      final Long decisionDefinitionKey, final CamundaAuthentication authentication) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toDecisionDefinition(
              decisionDefinitionServices.getByKey(decisionDefinitionKey, authentication)));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResponseEntity<Void> getDecisionDefinitionXML(
      final Long decisionDefinitionKey, final CamundaAuthentication authentication) {
    try {
      return (ResponseEntity)
          ResponseEntity.ok()
              .contentType(new MediaType(MediaType.TEXT_XML, StandardCharsets.UTF_8))
              .body(
                  decisionDefinitionServices.getDecisionDefinitionXml(
                      decisionDefinitionKey, authentication));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> evaluateDecision(
      final GeneratedDecisionEvaluationInstructionStrictContract request,
      final CamundaAuthentication authentication) {
    return RequestMapper.toEvaluateDecisionRequest(request, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        decisionDefinitionServices.evaluateDecision(
                            mapped.decisionId(),
                            mapped.decisionKey(),
                            mapped.variables(),
                            mapped.tenantId(),
                            authentication),
                    ResponseMapper::toEvaluateDecisionResponse,
                    HttpStatus.OK));
  }
}

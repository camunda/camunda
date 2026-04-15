/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.mapper.MappingRuleMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleCreateRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleSearchQueryRequestContract;
import io.camunda.gateway.mapping.http.search.contract.generated.MappingRuleUpdateRequestContract;
import io.camunda.gateway.mapping.http.validator.MappingRuleRequestValidator;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.MappingRuleValidator;
import io.camunda.service.MappingRuleServices;
import io.camunda.zeebe.gateway.rest.controller.generated.MappingRuleServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultMappingRuleServiceAdapter implements MappingRuleServiceAdapter {

  private final MappingRuleServices mappingRuleServices;
  private final MappingRuleMapper mappingRuleMapper;

  public DefaultMappingRuleServiceAdapter(
      final MappingRuleServices mappingRuleServices,
      final IdentifierValidator identifierValidator) {
    this.mappingRuleServices = mappingRuleServices;
    mappingRuleMapper =
        new MappingRuleMapper(
            new MappingRuleRequestValidator(new MappingRuleValidator(identifierValidator)));
  }

  @Override
  public ResponseEntity<Object> createMappingRule(
      final MappingRuleCreateRequestContract mappingRuleCreateRequestStrict,
      final CamundaAuthentication authentication) {
    return mappingRuleMapper
        .toMappingRuleCreateRequest(mappingRuleCreateRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            converted ->
                RequestExecutor.executeSync(
                    () -> mappingRuleServices.createMappingRule(converted, authentication),
                    ResponseMapper::toMappingRuleCreateResponse,
                    HttpStatus.CREATED));
  }

  @Override
  public ResponseEntity<Object> updateMappingRule(
      final String mappingRuleId,
      final MappingRuleUpdateRequestContract mappingRuleUpdateRequestStrict,
      final CamundaAuthentication authentication) {
    return mappingRuleMapper
        .toMappingRuleUpdateRequest(mappingRuleId, mappingRuleUpdateRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            converted ->
                RequestExecutor.executeSync(
                    () -> mappingRuleServices.updateMappingRule(converted, authentication),
                    ResponseMapper::toMappingRuleUpdateResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Void> deleteMappingRule(
      final String mappingRuleId, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () -> mappingRuleServices.deleteMappingRule(mappingRuleId, authentication));
  }

  @Override
  public ResponseEntity<Object> getMappingRule(
      final String mappingRuleId, final CamundaAuthentication authentication) {
    try {
      final var result = mappingRuleServices.getMappingRule(mappingRuleId, authentication);
      return ResponseEntity.ok(SearchQueryResponseMapper.toMappingRule(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @Override
  public ResponseEntity<Object> searchMappingRule(
      final MappingRuleSearchQueryRequestContract mappingRuleSearchQueryRequestStrict,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toMappingRuleQueryStrict(mappingRuleSearchQueryRequestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = mappingRuleServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toMappingRuleSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }
}

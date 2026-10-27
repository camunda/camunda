/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.mapper;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.validator.MappingRuleRequestValidator;
import io.camunda.gateway.protocol.model.MappingRuleCreateRequest;
import io.camunda.gateway.protocol.model.MappingRuleUpdateRequest;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.zeebe.util.Either;
import org.springframework.http.ProblemDetail;

public class MappingRuleMapper {

  private final MappingRuleRequestValidator mappingRuleRequestValidator;

  public MappingRuleMapper(final MappingRuleRequestValidator requestValidator) {
    mappingRuleRequestValidator = requestValidator;
  }

  public Either<ProblemDetail, MappingRuleDTO> toMappingRuleCreateRequest(
      final MappingRuleCreateRequest request) {
    return RequestMapper.getResult(
        mappingRuleRequestValidator.validateCreateRequest(request),
        () ->
            new MappingRuleDTO(
                request.getClaimName(),
                request.getClaimValue(),
                request.getName(),
                request.getMappingRuleId()));
  }

  public Either<ProblemDetail, MappingRuleDTO> toMappingRuleUpdateRequest(
      final String mappingRuleId, final MappingRuleUpdateRequest request) {
    return RequestMapper.getResult(
        mappingRuleRequestValidator.validateUpdateRequest(request),
        () ->
            new MappingRuleDTO(
                request.getClaimName(), request.getClaimValue(), request.getName(), mappingRuleId));
  }
}

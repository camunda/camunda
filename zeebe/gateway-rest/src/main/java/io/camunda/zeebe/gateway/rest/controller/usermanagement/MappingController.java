/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.usermanagement;

import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.zeebe.gateway.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/mapping-rules")
public class MappingController {
  private final MappingServices mappingServices;

  public MappingController(final MappingServices mappingServices) {
    this.mappingServices = mappingServices;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> create(
      @RequestBody final MappingRuleCreateRequest mappingRequest) {
    return RequestMapper.toMappingDTO(mappingRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createMapping);
  }

  private CompletableFuture<ResponseEntity<Object>> createMapping(final MappingDTO request) {
    return RequestMapper.executeServiceMethod(
        () ->
            mappingServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createMapping(request),
        ResponseMapper::toMappingCreateResponse);
  }
}

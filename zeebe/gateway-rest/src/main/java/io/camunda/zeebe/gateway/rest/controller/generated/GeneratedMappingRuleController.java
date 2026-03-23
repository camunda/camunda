/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleCreateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedMappingRuleUpdateRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedMappingRuleController {

  private final MappingRuleServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedMappingRuleController(
      final MappingRuleServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/mapping-rules",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> createMappingRule(
      @RequestBody final GeneratedMappingRuleCreateRequestStrictContract mappingRuleCreateRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createMappingRule(mappingRuleCreateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PUT,
      value = "/mapping-rules/{mappingRuleId}",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> updateMappingRule(
      @PathVariable("mappingRuleId") final String mappingRuleId,
      @RequestBody final GeneratedMappingRuleUpdateRequestStrictContract mappingRuleUpdateRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateMappingRule(
        mappingRuleId, mappingRuleUpdateRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.DELETE,
      value = "/mapping-rules/{mappingRuleId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Void> deleteMappingRule(
      @PathVariable("mappingRuleId") final String mappingRuleId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteMappingRule(mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/mapping-rules/{mappingRuleId}",
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> getMappingRule(
      @PathVariable("mappingRuleId") final String mappingRuleId) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getMappingRule(mappingRuleId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/mapping-rules/search",
      consumes = {"application/json"},
      produces = {"application/json", "application/problem+json"})
  public ResponseEntity<Object> searchMappingRule(
      @RequestBody
          final GeneratedMappingRuleSearchQueryRequestStrictContract
              mappingRuleSearchQueryRequest) {
    final var authentication = authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchMappingRule(mappingRuleSearchQueryRequest, authentication);
  }
}

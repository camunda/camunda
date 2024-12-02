/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.tenant;

import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.gateway.protocol.rest.TenantCreateRequest;
import io.camunda.zeebe.gateway.protocol.rest.TenantUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CamundaRestController
@RequestMapping("/v2/tenants")
public class TenantController {
  private final TenantServices tenantServices;

  public TenantController(final TenantServices tenantServices) {
    this.tenantServices = tenantServices;
  }

  @PostMapping(
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> createTenant(
      @RequestBody final TenantCreateRequest createTenantRequest) {
    return RequestMapper.toTenantCreateDto(createTenantRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::createTenant);
  }

  private CompletableFuture<ResponseEntity<Object>> createTenant(final TenantDTO tenantDTO) {
    return RequestMapper.executeServiceMethod(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .createTenant(tenantDTO),
        ResponseMapper::toTenantCreateResponse);
  }

  @PatchMapping(
      path = "/{tenantKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> updateTenant(
      @PathVariable final long tenantKey,
      @RequestBody final TenantUpdateRequest tenantUpdateRequest) {
    return RequestMapper.toTenantUpdateDto(tenantKey, tenantUpdateRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateTenant);
  }

  @DeleteMapping(
      path = "/{tenantKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> deleteTenant(
      @PathVariable final long tenantKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteTenant(tenantKey));
  }

  private CompletableFuture<ResponseEntity<Object>> updateTenant(final TenantDTO tenantDTO) {
    return RequestMapper.executeServiceMethod(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateTenant(tenantDTO),
        ResponseMapper::toTenantUpdateResponse);
  }

  // The API will accept only a single userKey for now to maintain atomicity and align
  // with REST principles.
  // Bulk operations would require adapting the broker request and adding a new event like
  // ENTITIES_ADDED, which is out of scope for this iteration.
  @PutMapping(
      path = "/{tenantKey}/users/{userKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> assignUsersToTenant(
      @PathVariable final long tenantKey, @PathVariable final long userKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .addMember(tenantKey, EntityType.USER, userKey));
  }

  @DeleteMapping(
      path = "/{tenantKey}/users/{userKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> removeUserFromTenant(
      @PathVariable final long tenantKey, @PathVariable final long userKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(tenantKey, EntityType.USER, userKey));
  }

  @PutMapping(
      path = "/{tenantKey}/mapping-rules/{mappingKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> assignMappingToTenant(
      @PathVariable final long tenantKey, @PathVariable final long mappingKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .addMember(tenantKey, EntityType.MAPPING, mappingKey));
  }

  @DeleteMapping(
      path = "/{tenantKey}/mapping-rules/{mappingKey}",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE})
  public CompletableFuture<ResponseEntity<Object>> removeMappingFromTenant(
      @PathVariable final long tenantKey, @PathVariable final long mappingKey) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            tenantServices
                .withAuthentication(RequestMapper.getAuthentication())
                .removeMember(tenantKey, EntityType.MAPPING, mappingKey));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.DeleteResourceRequest;
import io.camunda.gateway.protocol.model.ResourceSearchQuery;
import io.camunda.gateway.protocol.model.ResourceSearchQueryResult;
import io.camunda.search.query.DeployedResourceQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@CamundaRestController
@RequestMapping("/v2")
public class ResourceController {

  private final ServiceRegistry registry;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ResourceController(
      final ServiceRegistry registry,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.registry = registry;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/deployments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompletableFuture<ResponseEntity<Object>> deployResources(
      @PhysicalTenantId final String physicalTenantId,
      @RequestPart("resources") final List<MultipartFile> resources,
      @RequestPart(value = "tenantId", required = false) final String tenantId) {

    return RequestMapper.toDeployResourceRequest(
            resources, tenantId, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> deployResources(registry.resourceServices(physicalTenantId), request));
  }

  @CamundaPostMapping(path = "/resources/{resourceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteResource(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long resourceKey,
      @RequestBody(required = false) final DeleteResourceRequest deleteRequest) {
    return RequestMapper.toResourceDeletion(resourceKey, deleteRequest)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            request -> delete(registry.resourceServices(physicalTenantId), request));
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}")
  public CompletableFuture<ResponseEntity<Object>> getResource(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long resourceKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> registry.resourceServices(physicalTenantId).getByKey(resourceKey, authentication),
        ResponseMapper::toGetResourceResponse,
        HttpStatus.OK);
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}/content")
  public CompletableFuture<ResponseEntity<Object>> getResourceContent(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long resourceKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            registry
                .resourceServices(physicalTenantId)
                .getContentByKeyFilteredByType(resourceKey, "rpa", authentication),
        entity ->
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(ResponseMapper.toGetResourceContentResponse(entity)));
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}/content/binary")
  public CompletableFuture<ResponseEntity<Object>> getResourceContentBinary(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long resourceKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            registry
                .resourceServices(physicalTenantId)
                .getContentByKey(resourceKey, authentication),
        entity ->
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(ResponseMapper.toGetResourceContentResponse(entity)));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/resources/search")
  public ResponseEntity<ResourceSearchQueryResult> searchResources(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final ResourceSearchQuery query) {
    return SearchQueryRequestMapper.toDeployedResourceQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(registry.resourceServices(physicalTenantId), q));
  }

  private ResponseEntity<ResourceSearchQueryResult> search(
      final ResourceServices resourceServices, final DeployedResourceQuery query) {
    try {
      final var result =
          resourceServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toResourceSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> deployResources(
      final ResourceServices resourceServices, final DeployResourcesRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> resourceServices.deployResources(request, authentication),
        ResponseMapper::toDeployResourceResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> delete(
      final ResourceServices resourceServices, final ResourceDeletionRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> resourceServices.deleteResource(request, authentication),
        ResponseMapper::toDeleteResourceResponse,
        HttpStatus.OK);
  }
}

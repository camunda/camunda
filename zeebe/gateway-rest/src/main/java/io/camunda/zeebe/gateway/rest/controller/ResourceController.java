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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenant;
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

  private final ResourceServices resourceServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ResourceController(
      final ResourceServices resourceServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.resourceServices = resourceServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/deployments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompletableFuture<ResponseEntity<Object>> deployResources(
      @RequestPart("resources") final List<MultipartFile> resources,
      @RequestPart(value = "tenantId", required = false) final String tenantId,
      @PhysicalTenant final String physicalTenantId) {

    return RequestMapper.toDeployResourceRequest(
            resources, tenantId, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            req -> deployResources(req, physicalTenantId));
  }

  @CamundaPostMapping(path = "/resources/{resourceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteResource(
      @PathVariable final long resourceKey,
      @RequestBody(required = false) final DeleteResourceRequest deleteRequest,
      @PhysicalTenant final String physicalTenantId) {
    return RequestMapper.toResourceDeletion(resourceKey, deleteRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, req -> delete(req, physicalTenantId));
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}")
  public CompletableFuture<ResponseEntity<Object>> getResource(
      @PathVariable final long resourceKey, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> resourceServices.getByKey(resourceKey, authentication, physicalTenantId),
        ResponseMapper::toGetResourceResponse,
        HttpStatus.OK);
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}/content")
  public CompletableFuture<ResponseEntity<Object>> getResourceContent(
      @PathVariable final long resourceKey, @PhysicalTenant final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> resourceServices.getContentByKey(resourceKey, authentication, physicalTenantId),
        entity ->
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(ResponseMapper.toGetResourceContentResponse(entity)));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/resources/search")
  public ResponseEntity<ResourceSearchQueryResult> searchResources(
      @RequestBody(required = false) final ResourceSearchQuery query,
      @PhysicalTenant final String physicalTenantId) {
    return SearchQueryRequestMapper.toDeployedResourceQuery(query)
        .fold(RestErrorMapper::mapProblemToResponse, q -> search(q, physicalTenantId));
  }

  private ResponseEntity<ResourceSearchQueryResult> search(
      final DeployedResourceQuery query, final String physicalTenantId) {
    try {
      final var result =
          resourceServices.search(
              query, authenticationProvider.getCamundaAuthentication(), physicalTenantId);
      return ResponseEntity.ok(SearchQueryResponseMapper.toResourceSearchQueryResponse(result));
    } catch (final Exception e) {
      return RestErrorMapper.mapErrorToResponse(e);
    }
  }

  private CompletableFuture<ResponseEntity<Object>> deployResources(
      final DeployResourcesRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> resourceServices.deployResources(request, authentication, physicalTenantId),
        ResponseMapper::toDeployResourceResponse,
        HttpStatus.OK);
  }

  private CompletableFuture<ResponseEntity<Object>> delete(
      final ResourceDeletionRequest request, final String physicalTenantId) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () -> resourceServices.deleteResource(request, authentication, physicalTenantId),
        ResponseMapper::toDeleteResourceResponse,
        HttpStatus.OK);
  }
}

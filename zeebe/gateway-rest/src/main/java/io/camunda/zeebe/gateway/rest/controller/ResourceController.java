/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.service.ResourceServices.DeployResourcesRequest;
import io.camunda.service.ResourceServices.ResourceDeletionRequest;
import io.camunda.service.ResourceServices.ResourceFetchRequest;
import io.camunda.zeebe.gateway.protocol.rest.DeleteResourceRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.ResponseMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

  public ResourceController(
      final ResourceServices resourceServices, final MultiTenancyConfiguration multiTenancyCfg) {
    this.resourceServices = resourceServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping(path = "/deployments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public CompletableFuture<ResponseEntity<Object>> deployResources(
      @RequestPart("resources") final List<MultipartFile> resources,
      @RequestPart(value = "tenantId", required = false) final String tenantId) {

    return RequestMapper.toDeployResourceRequest(resources, tenantId, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::deployResources);
  }

  @CamundaPostMapping(path = "/resources/{resourceKey}/deletion")
  public CompletableFuture<ResponseEntity<Object>> deleteResource(
      @PathVariable final long resourceKey,
      @RequestBody(required = false) final DeleteResourceRequest deleteRequest) {
    return RequestMapper.toResourceDeletion(resourceKey, deleteRequest)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::delete);
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}")
  public CompletableFuture<ResponseEntity<Object>> getResource(
      @PathVariable final long resourceKey) {
    return RequestMapper.executeServiceMethod(
        () -> fetchResource(resourceKey), ResponseMapper::toGetResourceResponse);
  }

  @CamundaGetMapping(path = "/resources/{resourceKey}/content")
  public CompletableFuture<ResponseEntity<Object>> getResourceContent(
      @PathVariable final long resourceKey) {
    return RequestMapper.executeServiceMethod(
        () -> fetchResource(resourceKey), ResponseMapper::toGetResourceContentResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> deployResources(
      final DeployResourcesRequest request) {
    return RequestMapper.executeServiceMethod(
        () ->
            resourceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deployResources(request),
        ResponseMapper::toDeployResourceResponse);
  }

  private CompletableFuture<ResponseEntity<Object>> delete(final ResourceDeletionRequest request) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            resourceServices
                .withAuthentication(RequestMapper.getAuthentication())
                .deleteResource(request));
  }

  private CompletableFuture<ResourceRecord> fetchResource(final long resourceKey) {
    return resourceServices
        .withAuthentication(RequestMapper.getAuthentication())
        .fetchResource(new ResourceFetchRequest(resourceKey));
  }
}

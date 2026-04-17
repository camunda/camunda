/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.DeleteResourceRequest;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.camunda.zeebe.gateway.rest.controller.generated.ResourceServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.http.Part;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultResourceServiceAdapter implements ResourceServiceAdapter {

  private final ResourceServices resourceServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultResourceServiceAdapter(
      final ResourceServices resourceServices, final MultiTenancyConfiguration multiTenancyCfg) {
    this.resourceServices = resourceServices;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @Override
  public ResponseEntity<Object> createDeployment(
      final List<Part> resources,
      final String tenantId,
      final CamundaAuthentication authentication) {
    return RequestMapper.toDeployResourceRequestFromParts(
            resources, tenantId, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            request ->
                RequestExecutor.executeSync(
                    () -> resourceServices.deployResources(request, authentication),
                    ResponseMapper::toDeployResourceResponse,
                    HttpStatus.OK));
  }

  @Override
  public ResponseEntity<Object> getResource(
      final Long resourceKey, final CamundaAuthentication authentication) {
    return RequestExecutor.executeSync(
        () ->
            resourceServices.fetchResource(
                new ResourceServices.ResourceFetchRequest(resourceKey), authentication),
        ResponseMapper::toGetResourceResponse,
        HttpStatus.OK);
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResponseEntity<Void> getResourceContent(
      final Long resourceKey, final CamundaAuthentication authentication) {
    return (ResponseEntity)
        RequestExecutor.executeSync(
            () ->
                resourceServices.fetchResource(
                    new ResourceServices.ResourceFetchRequest(resourceKey), authentication),
            ResponseMapper::toGetResourceContentResponse,
            HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Object> deleteResource(
      final Long resourceKey,
      final DeleteResourceRequest deleteRequest,
      final CamundaAuthentication authentication) {
    return RequestMapper.toResourceDeletion(resourceKey, deleteRequest)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () -> resourceServices.deleteResource(mapped, authentication),
                    ResponseMapper::toDeleteResourceResponse,
                    HttpStatus.OK));
  }
}

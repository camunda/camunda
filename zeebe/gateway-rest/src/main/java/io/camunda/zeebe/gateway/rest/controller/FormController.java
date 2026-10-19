/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.FormResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/forms")
public class FormController {

  private static final String DEFAULT_TENANT_ID = "<default>";

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public FormController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{formKey}")
  public ResponseEntity<FormResult> getFormByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("formKey") final Long formKey) {
    try {
      final var form =
          serviceRegistry
              .formServices(physicalTenantId)
              .getByKey(formKey, authenticationProvider.getCamundaAuthentication());

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toFormItem(form));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/{formId}/latest")
  public ResponseEntity<FormResult> getLatestFormByFormId(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("formId") final String formId,
      @RequestParam(name = "tenantId", defaultValue = DEFAULT_TENANT_ID) final String tenantId) {
    try {
      final var form =
          serviceRegistry
              .formServices(physicalTenantId)
              .getLatestVersionByFormIdAndTenantId(
                  formId, tenantId, authenticationProvider.getCamundaAuthentication())
              .orElseThrow(
                  () ->
                      new ServiceException(
                          "Form with ID '%s' not found for tenant '%s'".formatted(formId, tenantId),
                          Status.NOT_FOUND));

      return ResponseEntity.ok().body(SearchQueryResponseMapper.toFormItem(form));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

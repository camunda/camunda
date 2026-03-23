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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDeleteResourceRequestStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import jakarta.servlet.http.Part;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedResourceController {

  private final ResourceServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedResourceController(
      final ResourceServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/deployments",
      consumes = { MediaType.MULTIPART_FORM_DATA_VALUE },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> createDeployment(
      @RequestPart("resources") final List<Part> resources,
      @RequestPart(value = "tenantId", required = false) final String tenantId
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.createDeployment(resources, tenantId, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/resources/{resourceKey}",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getResource(
      @PathVariable("resourceKey") final Long resourceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getResource(resourceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/resources/{resourceKey}/content",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> getResourceContent(
      @PathVariable("resourceKey") final Long resourceKey
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getResourceContent(resourceKey, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/resources/{resourceKey}/deletion",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> deleteResource(
      @PathVariable("resourceKey") final Long resourceKey,
      @RequestBody(required = false) final GeneratedDeleteResourceRequestStrictContract deleteResourceRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.deleteResource(resourceKey, deleteResourceRequest, authentication);
  }
}

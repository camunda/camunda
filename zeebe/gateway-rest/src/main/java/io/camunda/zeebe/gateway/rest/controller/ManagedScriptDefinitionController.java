/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition.ManagedScriptDefinitionRecord;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/managed-script-definitions")
public class ManagedScriptDefinitionController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ManagedScriptDefinitionController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/activation")
  public CompletableFuture<ResponseEntity<Object>> activate(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody final ActivationRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .managedScriptDefinitionServices(physicalTenantId)
                .activate(
                    request.provider(),
                    request.worker(),
                    request.leaseDuration(),
                    request.maxDefinitions(),
                    request.tenantId() == null
                        ? TenantOwned.DEFAULT_TENANT_IDENTIFIER
                        : request.tenantId(),
                    authentication),
        definitions ->
            new ActivationResponse(definitions.stream().map(DefinitionResponse::from).toList()),
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/{definitionKey}/lease")
  public CompletableFuture<ResponseEntity<Object>> renewLease(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long definitionKey,
      @RequestBody final LeaseRenewalRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .managedScriptDefinitionServices(physicalTenantId)
                .renewLease(
                    definitionKey,
                    request.revision(),
                    request.leaseToken(),
                    request.leaseDuration(),
                    authentication),
        DefinitionResponse::from,
        HttpStatus.OK);
  }

  @CamundaPostMapping(path = "/{definitionKey}/transitions")
  public CompletableFuture<ResponseEntity<Object>> transition(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable final long definitionKey,
      @RequestBody final TransitionRequest request) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    final var services = serviceRegistry.managedScriptDefinitionServices(physicalTenantId);
    return switch (request.status()) {
      case DEPLOYING ->
          RequestExecutor.executeServiceMethod(
              () ->
                  services.checkpointDeployment(
                      definitionKey,
                      request.revision(),
                      request.leaseToken(),
                      request.operationId(),
                      request.providerOperationId(),
                      authentication),
              DefinitionResponse::from,
              HttpStatus.OK);
      case READY ->
          RequestExecutor.executeServiceMethod(
              () ->
                  services.completeDeployment(
                      definitionKey,
                      request.revision(),
                      request.leaseToken(),
                      request.operationId(),
                      request.providerDeploymentId(),
                      authentication),
              DefinitionResponse::from,
              HttpStatus.OK);
      case FAILED ->
          RequestExecutor.executeServiceMethod(
              () ->
                  services.failDeployment(
                      definitionKey,
                      request.revision(),
                      request.leaseToken(),
                      request.operationId(),
                      request.failureCode(),
                      request.failureMessage(),
                      request.retryable(),
                      authentication),
              DefinitionResponse::from,
              HttpStatus.OK);
      default ->
          CompletableFuture.completedFuture(
              ResponseEntity.badRequest()
                  .body(
                      "Expected transition status to be DEPLOYING, READY, or FAILED, but was %s"
                          .formatted(request.status())));
    };
  }

  @CamundaGetMapping(path = "/{definitionKey}")
  public CompletableFuture<ResponseEntity<Object>> get(
      @PhysicalTenantId final String physicalTenantId, @PathVariable final long definitionKey) {
    final var authentication = authenticationProvider.getCamundaAuthentication();
    return RequestExecutor.executeServiceMethod(
        () ->
            serviceRegistry
                .managedScriptDefinitionServices(physicalTenantId)
                .get(definitionKey, authentication),
        DefinitionResponse::from,
        HttpStatus.OK);
  }

  public record ActivationRequest(
      String provider, String worker, long leaseDuration, int maxDefinitions, String tenantId) {}

  public record ActivationResponse(List<DefinitionResponse> definitions) {}

  public record LeaseRenewalRequest(long revision, String leaseToken, long leaseDuration) {}

  public record TransitionRequest(
      long revision,
      String leaseToken,
      String operationId,
      ManagedScriptDefinitionStatus status,
      String providerOperationId,
      String providerDeploymentId,
      String failureCode,
      String failureMessage,
      boolean retryable) {}

  public record DefinitionResponse(
      long managedScriptDefinitionKey,
      ManagedScriptDefinitionStatus status,
      long revision,
      long resourceKey,
      String resourceName,
      String artifactDigest,
      String elementId,
      String bpmnProcessId,
      long processDefinitionKey,
      int processDefinitionVersion,
      String processDefinitionVersionTag,
      String language,
      String runtime,
      String provider,
      String leaseOwner,
      String leaseToken,
      long leaseExpiresAt,
      String providerOperationId,
      String providerDeploymentId,
      String failureCode,
      String failureMessage,
      boolean retryable,
      String tenantId) {

    private static DefinitionResponse from(final ManagedScriptDefinitionRecord definition) {
      return new DefinitionResponse(
          definition.getManagedScriptDefinitionKey(),
          definition.getStatus(),
          definition.getRevision(),
          definition.getResourceKey(),
          definition.getResourceName(),
          HexFormat.of().formatHex(definition.getArtifactDigest()),
          definition.getElementId(),
          definition.getBpmnProcessId(),
          definition.getProcessDefinitionKey(),
          definition.getProcessDefinitionVersion(),
          definition.getProcessDefinitionVersionTag(),
          definition.getLanguage(),
          definition.getRuntime(),
          definition.getProvider(),
          definition.getLeaseOwner(),
          definition.getLeaseToken(),
          definition.getLeaseExpiresAt(),
          definition.getProviderOperationId(),
          definition.getProviderDeploymentId(),
          definition.getFailureCode(),
          definition.getFailureMessage(),
          definition.isRetryable(),
          definition.getTenantId());
    }
  }
}

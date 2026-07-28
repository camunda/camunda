/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.search.entities.AuditLogEntity.AuditLogEntityType.VARIABLE;
import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.VariableSearchQuery;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.mapper.UpdateMetadataMapper;
import io.camunda.zeebe.gateway.rest.mapper.UpdateMetadataMapper.ResolvedMetadata;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/variables")
public class VariableController {

  private final ServiceRegistry serviceRegistry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GatewayRestConfiguration gatewayRestConfiguration;

  public VariableController(
      final ServiceRegistry serviceRegistry,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.serviceRegistry = serviceRegistry;
    this.authenticationProvider = authenticationProvider;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
  }

  @CamundaPostMapping(path = "/search")
  public ResponseEntity<Object> searchVariables(
      @PhysicalTenantId final String physicalTenantId,
      @RequestBody(required = false) final VariableSearchQuery query,
      @RequestParam(name = "truncateValues", required = false, defaultValue = "true")
          final boolean truncateValues) {
    return SearchQueryRequestMapper.toVariableQuery(query)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            q -> search(physicalTenantId, q, truncateValues));
  }

  private ResponseEntity<Object> search(
      final String physicalTenantId, final VariableQuery query, final boolean truncateValues) {
    final var variableServices = serviceRegistry.variableServices(physicalTenantId);
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var result = variableServices.search(query, authentication);
      final Object response;
      if (gatewayRestConfiguration.getUpdateMetadata().isEnabled()) {
        final Function<VariableEntity, String> keyFn = v -> String.valueOf(v.variableKey());
        final var metadata =
            UpdateMetadataMapper.resolveAll(
                result.items(),
                keyFn,
                VARIABLE,
                serviceRegistry.auditLogServices(physicalTenantId),
                authentication);
        response =
            SearchQueryResponseMapper.toVariableSearchQueryResponse(
                result,
                truncateValues,
                v -> metadata.getOrDefault(keyFn.apply(v), ResolvedMetadata.EMPTY).updatedBy(),
                v -> metadata.getOrDefault(keyFn.apply(v), ResolvedMetadata.EMPTY).updatedAt());
      } else {
        response = SearchQueryResponseMapper.toVariableSearchQueryResponse(result, truncateValues);
      }
      return ResponseEntity.ok(response);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  @CamundaGetMapping(path = "/{variableKey}")
  public ResponseEntity<Object> getByKey(
      @PhysicalTenantId final String physicalTenantId,
      @PathVariable("variableKey") final Long variableKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var entity =
          serviceRegistry.variableServices(physicalTenantId).getByKey(variableKey, authentication);
      final Object response;
      if (gatewayRestConfiguration.getUpdateMetadata().isEnabled()) {
        final var metadata =
            UpdateMetadataMapper.resolve(
                entity,
                v -> String.valueOf(v.variableKey()),
                VARIABLE,
                serviceRegistry.auditLogServices(physicalTenantId),
                authentication);
        response =
            SearchQueryResponseMapper.toVariableItem(
                entity, metadata.updatedBy(), metadata.updatedAt());
      } else {
        response = SearchQueryResponseMapper.toVariableItem(entity);
      }
      // Success case: Return the left side with the VariableItem wrapped in ResponseEntity
      return ResponseEntity.ok().body(response);
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}

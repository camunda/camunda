/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static io.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;

import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import io.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefinitionClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DefinitionResponseDto getDefinitionByTypeAndKey(
      final DefinitionType definitionType, final DefinitionOptimizeResponseDto expectedDefinition) {
    return getRequestExecutor()
        .buildGetDefinitionByTypeAndKeyRequest(definitionType.getId(), expectedDefinition.getKey())
        .execute(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionResponseDto> getAllDefinitions() {
    return getAllDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionResponseDto> getAllDefinitionsAsUser(
      final String username, final String password) {
    return getRequestExecutor()
        .buildGetDefinitions()
        .withUserAuthentication(username, password)
        .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByType(
      final DefinitionType definitionType) {
    return getDefinitionKeysByType(definitionType, null);
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByType(
      final DefinitionType definitionType, final String filterByCollectionScope) {
    return getDefinitionKeysByTypeAsUser(
        definitionType, filterByCollectionScope, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByTypeAsUser(
      final DefinitionType definitionType, final String username, final String password) {
    return getDefinitionKeysByTypeAsUser(definitionType, null, username, password);
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByTypeAsUser(
      final DefinitionType definitionType,
      final String filterByCollectionScope,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildGetDefinitionKeysByType(definitionType.getId(), filterByCollectionScope)
        .withUserAuthentication(username, password)
        .executeAndReturnList(DefinitionKeyResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKey(
      final DefinitionType type, final String key) {
    return getDefinitionVersionsByTypeAndKey(type, key, null);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKey(
      final DefinitionType type, final String key, final String filterByCollectionScope) {
    return getDefinitionVersionsByTypeAndKeyAsUser(
        type, key, filterByCollectionScope, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKeyAsUser(
      final DefinitionType type, final String key, final String username, final String password) {
    return getDefinitionVersionsByTypeAndKeyAsUser(type, key, null, username, password);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKeyAsUser(
      final DefinitionType type,
      final String key,
      final String filterByCollectionScope,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildGetDefinitionVersionsByTypeAndKeyRequest(type.getId(), key, filterByCollectionScope)
        .withUserAuthentication(username, password)
        .executeAndReturnList(
            DefinitionVersionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionWithTenantsResponseDto>
      resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
          final DefinitionType type, final MultiDefinitionTenantsRequestDto request) {
    return resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
        type, request, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionWithTenantsResponseDto>
      resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
          final DefinitionType type,
          final MultiDefinitionTenantsRequestDto request,
          final String username,
          final String password) {
    return getRequestExecutor()
        .buildResolveDefinitionTenantsByTypeMultipleKeysAndVersionsRequest(type.getId(), request)
        .withUserAuthentication(username, password)
        .executeAndReturnList(
            DefinitionWithTenantsResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenant() {
    return getDefinitionsGroupedByTenantAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenantAsUser(
      final String username, final String password) {
    return getRequestExecutor()
        .buildGetDefinitionsGroupedByTenant()
        .withUserAuthentication(username, password)
        .executeAndReturnList(
            TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return getAllDecisionDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitionsAsUser(
      final String username, final String password) {
    return getRequestExecutor()
        .buildGetDecisionDefinitionsRequest()
        .withUserAuthentication(username, password)
        .executeAndReturnList(
            DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getAllProcessDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitionsAsUser(
      final String username, final String password) {
    return getRequestExecutor()
        .buildGetProcessDefinitionsRequest()
        .withUserAuthentication(username, password)
        .executeAndReturnList(
            ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  public String getDecisionDefinitionXml(final String key, final String version) {
    return getDecisionDefinitionXml(key, version, null);
  }

  public String getDecisionDefinitionXml(
      final String key, final String version, final String tenant) {
    return getRequestExecutor()
        .buildGetDecisionDefinitionXmlRequest(key, version, tenant)
        .execute(String.class, Response.Status.OK.getStatusCode());
  }

  public String getProcessDefinitionXml(
      final String key, final String version, final String tenantId) {
    return getProcessDefinitionXmlAsUser(
        key, version, tenantId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public String getProcessDefinitionXmlAsUser(
      final String key,
      final String version,
      final String tenantId,
      final String username,
      final String password) {
    return getRequestExecutor()
        .buildGetProcessDefinitionXmlRequest(key, version, tenantId)
        .withUserAuthentication(username, password)
        .execute(String.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}

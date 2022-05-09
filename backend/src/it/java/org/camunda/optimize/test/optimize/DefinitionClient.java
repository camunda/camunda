/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeResponseDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionKeyResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsResponseDto;
import org.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.DefinitionWithTenantsResponseDto;
import org.camunda.optimize.dto.optimize.rest.definition.MultiDefinitionTenantsRequestDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class DefinitionClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DefinitionResponseDto getDefinitionByTypeAndKey(DefinitionType definitionType,
                                                         DefinitionOptimizeResponseDto expectedDefinition) {
    return getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(
        definitionType.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionResponseDto> getAllDefinitions() {
    return getAllDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionKeyResponseDto> getCamundaEventImportedProcessDefinitionKeys() {
    return getRequestExecutor()
      .buildGetDefinitionKeysByType(DefinitionType.PROCESS.getId(), null, true)
      .executeAndReturnList(DefinitionKeyResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionResponseDto> getAllDefinitionsAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetDefinitions()
      .withUserAuthentication(username, password)
      .executeAndReturnList(DefinitionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByType(final DefinitionType definitionType) {
    return getDefinitionKeysByType(definitionType, null);
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByType(final DefinitionType definitionType,
                                                                final String filterByCollectionScope) {
    return getDefinitionKeysByTypeAsUser(
      definitionType, filterByCollectionScope, DEFAULT_USERNAME, DEFAULT_PASSWORD
    );
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByTypeAsUser(final DefinitionType definitionType,
                                                                      final String username,
                                                                      final String password) {
    return getDefinitionKeysByTypeAsUser(definitionType, null, username, password);
  }

  public List<DefinitionKeyResponseDto> getDefinitionKeysByTypeAsUser(final DefinitionType definitionType,
                                                                      final String filterByCollectionScope,
                                                                      final String username,
                                                                      final String password) {
    return getRequestExecutor()
      .buildGetDefinitionKeysByType(definitionType.getId(), filterByCollectionScope)
      .withUserAuthentication(username, password)
      .executeAndReturnList(DefinitionKeyResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKey(final DefinitionType type,
                                                                              final String key) {
    return getDefinitionVersionsByTypeAndKey(type, key, null);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKey(final DefinitionType type,
                                                                              final String key,
                                                                              final String filterByCollectionScope) {
    return getDefinitionVersionsByTypeAndKeyAsUser(
      type, key, filterByCollectionScope, DEFAULT_USERNAME, DEFAULT_PASSWORD
    );
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKeyAsUser(final DefinitionType type,
                                                                                    final String key,
                                                                                    final String username,
                                                                                    final String password) {
    return getDefinitionVersionsByTypeAndKeyAsUser(type, key, null, username, password);
  }

  public List<DefinitionVersionResponseDto> getDefinitionVersionsByTypeAndKeyAsUser(final DefinitionType type,
                                                                                    final String key,
                                                                                    final String filterByCollectionScope,
                                                                                    final String username,
                                                                                    final String password) {
    return getRequestExecutor()
      .buildGetDefinitionVersionsByTypeAndKeyRequest(type.getId(), key, filterByCollectionScope)
      .withUserAuthentication(username, password)
      .executeAndReturnList(DefinitionVersionResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionWithTenantsResponseDto> resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
    final DefinitionType type,
    final MultiDefinitionTenantsRequestDto request) {
    return resolveDefinitionTenantsByTypeMultipleKeyAndVersions(type, request, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionWithTenantsResponseDto> resolveDefinitionTenantsByTypeMultipleKeyAndVersions(
    final DefinitionType type,
    final MultiDefinitionTenantsRequestDto request,
    final String username,
    final String password) {
    return getRequestExecutor()
      .buildResolveDefinitionTenantsByTypeMultipleKeysAndVersionsRequest(type.getId(), request)
      .withUserAuthentication(username, password)
      .executeAndReturnList(DefinitionWithTenantsResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenant() {
    return getDefinitionsGroupedByTenantAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<TenantWithDefinitionsResponseDto> getDefinitionsGroupedByTenantAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .withUserAuthentication(username, password)
      .executeAndReturnList(TenantWithDefinitionsResponseDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitions() {
    return getAllDecisionDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DecisionDefinitionOptimizeDto> getAllDecisionDefinitionsAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetDecisionDefinitionsRequest()
      .withUserAuthentication(username, password)
      .executeAndReturnList(DecisionDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitions() {
    return getAllProcessDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<ProcessDefinitionOptimizeDto> getAllProcessDefinitionsAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetProcessDefinitionsRequest()
      .withUserAuthentication(username, password)
      .executeAndReturnList(ProcessDefinitionOptimizeDto.class, Response.Status.OK.getStatusCode());
  }

  public String getDecisionDefinitionXml(String key, String version) {
    return getDecisionDefinitionXml(key, version, null);
  }

  public String getDecisionDefinitionXml(String key, String version, String tenant) {
    return getRequestExecutor()
      .buildGetDecisionDefinitionXmlRequest(key, version, tenant)
      .execute(String.class, Response.Status.OK.getStatusCode());
  }

  public String getProcessDefinitionXml(String key, String version, String tenantId) {
    return getProcessDefinitionXmlAsUser(key, version, tenantId, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public String getProcessDefinitionXmlAsUser(String key, String version, String tenantId, String username,
                                              String password) {
    return getRequestExecutor()
      .buildGetProcessDefinitionXmlRequest(key, version, tenantId)
      .withUserAuthentication(username, password)
      .execute(String.class, Response.Status.OK.getStatusCode());
  }

  private OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutorSupplier.get();
  }
}

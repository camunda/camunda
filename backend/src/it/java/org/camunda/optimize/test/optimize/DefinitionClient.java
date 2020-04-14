/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.optimize;

import lombok.AllArgsConstructor;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionWithTenantsDto;
import org.camunda.optimize.dto.optimize.query.definition.TenantWithDefinitionsDto;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.function.Supplier;

import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;

@AllArgsConstructor
public class DefinitionClient {
  private final Supplier<OptimizeRequestExecutor> requestExecutorSupplier;

  public DefinitionWithTenantsDto getDefinitionByTypeAndKey(DefinitionType definitionType,
                                                            DefinitionOptimizeDto expectedDefinition) {
    return getRequestExecutor()
      .buildGetDefinitionByTypeAndKeyRequest(
        definitionType.getId(),
        expectedDefinition.getKey()
      )
      .execute(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
  }

  public List<DefinitionWithTenantsDto> getAllDefinitions() {
    return getAllDefinitionsAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<DefinitionWithTenantsDto> getAllDefinitionsAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetDefinitions()
      .withUserAuthentication(username, password)
      .executeAndReturnList(DefinitionWithTenantsDto.class, Response.Status.OK.getStatusCode());
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenant() {
    return getDefinitionsGroupedByTenantAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  public List<TenantWithDefinitionsDto> getDefinitionsGroupedByTenantAsUser(String username, String password) {
    return getRequestExecutor()
      .buildGetDefinitionsGroupedByTenant()
      .withUserAuthentication(username, password)
      .executeAndReturnList(TenantWithDefinitionsDto.class, Response.Status.OK.getStatusCode());
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

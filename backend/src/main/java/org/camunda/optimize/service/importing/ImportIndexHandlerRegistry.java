/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.service.importing.engine.handler.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.CompletedIncidentImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.CompletedProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.CompletedUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.DecisionInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.engine.handler.IdentityLinkLogImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.OpenIncidentImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.ProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.ProcessDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.RunningActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.RunningUserTaskInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.TenantImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.UserOperationLogImportIndexHandler;
import org.camunda.optimize.service.importing.engine.handler.VariableUpdateInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.ingested.handler.IngestedImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeIncidentImportIndexHandler;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeProcessDefinitionImportIndexHandler;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeProcessInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeVariableImportIndexHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ImportIndexHandlerRegistry {

  private IngestedImportIndexHandlerProvider ingestedImportIndexHandlerProvider = null;
  private Map<String, EngineImportIndexHandlerProvider> engineImportIndexHandlerProviderMap = new HashMap<>();
  private Map<Integer, ZeebeImportIndexHandlerProvider> zeebeImportIndexHandlerProviderMap = new HashMap<>();

  public void register(final IngestedImportIndexHandlerProvider ingestedImportIndexHandlerProvider) {
    this.ingestedImportIndexHandlerProvider = ingestedImportIndexHandlerProvider;
  }

  public void register(final String engineAlias,
                       final EngineImportIndexHandlerProvider engineImportIndexHandlerProvider) {
    engineImportIndexHandlerProviderMap.put(engineAlias, engineImportIndexHandlerProvider);
  }

  public void register(final int partitionId,
                       final ZeebeImportIndexHandlerProvider zeebeImportIndexHandlerProvider) {
    zeebeImportIndexHandlerProviderMap.put(partitionId, zeebeImportIndexHandlerProvider);
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers(String engineAlias) {
    return getEngineHandlers(engineAlias, EngineImportIndexHandlerProvider::getAllEntitiesBasedHandlers);
  }

  public List<TimestampBasedEngineImportIndexHandler> getTimestampEngineBasedHandlers(String engineAlias) {
    return getEngineHandlers(engineAlias, EngineImportIndexHandlerProvider::getTimestampBasedEngineHandlers);
  }

  public List<EngineImportIndexHandler<?, ?>> getAllEngineImportHandlers() {
    return engineImportIndexHandlerProviderMap.values()
      .stream()
      .flatMap(provider -> provider.getAllHandlers().stream())
      .collect(Collectors.toList());
  }

  public List<PositionBasedImportIndexHandler> getPositionBasedHandlers(Integer partitionId) {
    return Optional.ofNullable(zeebeImportIndexHandlerProviderMap.get(partitionId))
      .map(ZeebeImportIndexHandlerProvider::getPositionBasedEngineHandlers)
      .orElse(Collections.emptyList());
  }

  public Collection<ImportIndexHandler<?, ?>> getAllIngestedImportHandlers() {
    return ingestedImportIndexHandlerProvider.getAllHandlers();
  }

  public CompletedProcessInstanceImportIndexHandler getCompletedProcessInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, CompletedProcessInstanceImportIndexHandler.class);
  }

  public CompletedActivityInstanceImportIndexHandler getCompletedActivityInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, CompletedActivityInstanceImportIndexHandler.class);
  }

  public RunningActivityInstanceImportIndexHandler getRunningActivityInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, RunningActivityInstanceImportIndexHandler.class);
  }

  public CompletedIncidentImportIndexHandler getCompletedIncidentImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, CompletedIncidentImportIndexHandler.class);
  }

  public OpenIncidentImportIndexHandler getOpenIncidentImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, OpenIncidentImportIndexHandler.class);
  }

  public UserOperationLogImportIndexHandler getUserOperationsLogImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, UserOperationLogImportIndexHandler.class);
  }

  public RunningProcessInstanceImportIndexHandler getRunningProcessInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, RunningProcessInstanceImportIndexHandler.class);
  }

  public VariableUpdateInstanceImportIndexHandler getVariableUpdateInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, VariableUpdateInstanceImportIndexHandler.class);
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, ProcessDefinitionImportIndexHandler.class);
  }

  public CompletedUserTaskInstanceImportIndexHandler getCompletedUserTaskInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, CompletedUserTaskInstanceImportIndexHandler.class);
  }

  public RunningUserTaskInstanceImportIndexHandler getRunningUserTaskInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, RunningUserTaskInstanceImportIndexHandler.class);
  }

  public IdentityLinkLogImportIndexHandler getIdentityLinkImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, IdentityLinkLogImportIndexHandler.class);
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, ProcessDefinitionXmlImportIndexHandler.class);
  }

  public DecisionDefinitionImportIndexHandler getDecisionDefinitionImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, DecisionDefinitionImportIndexHandler.class);
  }

  public DecisionDefinitionXmlImportIndexHandler getDecisionDefinitionXmlImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, DecisionDefinitionXmlImportIndexHandler.class);
  }

  public DecisionInstanceImportIndexHandler getDecisionInstanceImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, DecisionInstanceImportIndexHandler.class);
  }

  public TenantImportIndexHandler getTenantImportIndexHandler(String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, TenantImportIndexHandler.class);
  }

  public ZeebeProcessDefinitionImportIndexHandler getZeebeProcessDefinitionImportIndexHandler(Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeProcessDefinitionImportIndexHandler.class);
  }

  public ZeebeProcessInstanceImportIndexHandler getZeebeProcessInstanceImportIndexHandler(Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeProcessInstanceImportIndexHandler.class);
  }

  public ZeebeIncidentImportIndexHandler getZeebeIncidentImportIndexHandler(Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeIncidentImportIndexHandler.class);
  }

  public ZeebeVariableImportIndexHandler getZeebeVariableImportIndexHandler(Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeVariableImportIndexHandler.class);
  }

  public ExternalVariableUpdateImportIndexHandler getExternalVariableUpdateImportIndexHandler() {
    return ingestedImportIndexHandlerProvider.getImportIndexHandler(ExternalVariableUpdateImportIndexHandler.class);
  }

  public void reloadConfiguration() {
    this.ingestedImportIndexHandlerProvider = null;
    this.engineImportIndexHandlerProviderMap = new HashMap<>();
    this.zeebeImportIndexHandlerProviderMap = new HashMap<>();
  }

  private <T extends EngineImportIndexHandler> List<T> getEngineHandlers(
    String engineAlias, Function<EngineImportIndexHandlerProvider, List<T>> getHandlerFunction) {
    return Optional.ofNullable(engineImportIndexHandlerProviderMap.get(engineAlias))
      .map(getHandlerFunction)
      .orElse(null);
  }

  private <T extends EngineImportIndexHandler> T getEngineImportIndexHandler(final String engineAlias,
                                                                             final Class<T> handlerClass) {
    return Optional.ofNullable(engineImportIndexHandlerProviderMap.get(engineAlias))
      .map(provider -> provider.getImportIndexHandler(handlerClass))
      .orElse(null);
  }

  private <T extends ZeebeImportIndexHandler> T getZeebeImportIndexHandler(final Integer partitionId,
                                                                           final Class<T> handlerClass) {
    return Optional.ofNullable(zeebeImportIndexHandlerProviderMap.get(partitionId))
      .map(provider -> provider.getImportIndexHandler(handlerClass))
      .orElse(null);
  }

}

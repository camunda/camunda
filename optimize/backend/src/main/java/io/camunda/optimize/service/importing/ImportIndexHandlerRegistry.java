/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.service.importing.engine.handler.CompletedActivityInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.CompletedIncidentImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.CompletedProcessInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.CompletedUserTaskInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.DecisionDefinitionImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.DecisionDefinitionXmlImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.DecisionInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.engine.handler.IdentityLinkLogImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.OpenIncidentImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.ProcessDefinitionImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.ProcessDefinitionXmlImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.RunningActivityInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.RunningProcessInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.RunningUserTaskInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.TenantImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.UserOperationLogImportIndexHandler;
import io.camunda.optimize.service.importing.engine.handler.VariableUpdateInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.ingested.handler.IngestedImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeIncidentImportIndexHandler;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeProcessDefinitionImportIndexHandler;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeProcessInstanceImportIndexHandler;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeUserTaskImportIndexHandler;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeVariableImportIndexHandler;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ImportIndexHandlerRegistry {

  private IngestedImportIndexHandlerProvider ingestedImportIndexHandlerProvider = null;
  private Map<String, EngineImportIndexHandlerProvider> engineImportIndexHandlerProviderMap =
      new HashMap<>();
  private Map<Integer, ZeebeImportIndexHandlerProvider> zeebeImportIndexHandlerProviderMap =
      new HashMap<>();

  public void register(
      final IngestedImportIndexHandlerProvider ingestedImportIndexHandlerProvider) {
    this.ingestedImportIndexHandlerProvider = ingestedImportIndexHandlerProvider;
  }

  public void register(
      final String engineAlias,
      final EngineImportIndexHandlerProvider engineImportIndexHandlerProvider) {
    engineImportIndexHandlerProviderMap.put(engineAlias, engineImportIndexHandlerProvider);
  }

  public void register(
      final int partitionId,
      final ZeebeImportIndexHandlerProvider zeebeImportIndexHandlerProvider) {
    zeebeImportIndexHandlerProviderMap.put(partitionId, zeebeImportIndexHandlerProvider);
  }

  public List<AllEntitiesBasedImportIndexHandler> getAllEntitiesBasedHandlers(
      final String engineAlias) {
    return getEngineHandlers(
        engineAlias, EngineImportIndexHandlerProvider::getAllEntitiesBasedHandlers);
  }

  public List<TimestampBasedEngineImportIndexHandler> getTimestampEngineBasedHandlers(
      final String engineAlias) {
    return getEngineHandlers(
        engineAlias, EngineImportIndexHandlerProvider::getTimestampBasedEngineHandlers);
  }

  public List<EngineImportIndexHandler<?, ?>> getAllEngineImportHandlers() {
    return engineImportIndexHandlerProviderMap.values().stream()
        .flatMap(provider -> provider.getAllHandlers().stream())
        .collect(Collectors.toList());
  }

  public List<PositionBasedImportIndexHandler> getPositionBasedHandlers(final Integer partitionId) {
    return Optional.ofNullable(zeebeImportIndexHandlerProviderMap.get(partitionId))
        .map(ZeebeImportIndexHandlerProvider::getPositionBasedEngineHandlers)
        .orElse(Collections.emptyList());
  }

  public Collection<ImportIndexHandler<?, ?>> getAllIngestedImportHandlers() {
    return ingestedImportIndexHandlerProvider.getAllHandlers();
  }

  public CompletedProcessInstanceImportIndexHandler getCompletedProcessInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(
        engineAlias, CompletedProcessInstanceImportIndexHandler.class);
  }

  public CompletedActivityInstanceImportIndexHandler getCompletedActivityInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(
        engineAlias, CompletedActivityInstanceImportIndexHandler.class);
  }

  public RunningActivityInstanceImportIndexHandler getRunningActivityInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(
        engineAlias, RunningActivityInstanceImportIndexHandler.class);
  }

  public CompletedIncidentImportIndexHandler getCompletedIncidentImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, CompletedIncidentImportIndexHandler.class);
  }

  public OpenIncidentImportIndexHandler getOpenIncidentImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, OpenIncidentImportIndexHandler.class);
  }

  public UserOperationLogImportIndexHandler getUserOperationsLogImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, UserOperationLogImportIndexHandler.class);
  }

  public RunningProcessInstanceImportIndexHandler getRunningProcessInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, RunningProcessInstanceImportIndexHandler.class);
  }

  public VariableUpdateInstanceImportIndexHandler getVariableUpdateInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, VariableUpdateInstanceImportIndexHandler.class);
  }

  public ProcessDefinitionImportIndexHandler getProcessDefinitionImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, ProcessDefinitionImportIndexHandler.class);
  }

  public CompletedUserTaskInstanceImportIndexHandler getCompletedUserTaskInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(
        engineAlias, CompletedUserTaskInstanceImportIndexHandler.class);
  }

  public RunningUserTaskInstanceImportIndexHandler getRunningUserTaskInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(
        engineAlias, RunningUserTaskInstanceImportIndexHandler.class);
  }

  public IdentityLinkLogImportIndexHandler getIdentityLinkImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, IdentityLinkLogImportIndexHandler.class);
  }

  public ProcessDefinitionXmlImportIndexHandler getProcessDefinitionXmlImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, ProcessDefinitionXmlImportIndexHandler.class);
  }

  public DecisionDefinitionImportIndexHandler getDecisionDefinitionImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, DecisionDefinitionImportIndexHandler.class);
  }

  public DecisionDefinitionXmlImportIndexHandler getDecisionDefinitionXmlImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, DecisionDefinitionXmlImportIndexHandler.class);
  }

  public DecisionInstanceImportIndexHandler getDecisionInstanceImportIndexHandler(
      final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, DecisionInstanceImportIndexHandler.class);
  }

  public TenantImportIndexHandler getTenantImportIndexHandler(final String engineAlias) {
    return getEngineImportIndexHandler(engineAlias, TenantImportIndexHandler.class);
  }

  public ZeebeProcessDefinitionImportIndexHandler getZeebeProcessDefinitionImportIndexHandler(
      final Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeProcessDefinitionImportIndexHandler.class);
  }

  public ZeebeProcessInstanceImportIndexHandler getZeebeProcessInstanceImportIndexHandler(
      final Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeProcessInstanceImportIndexHandler.class);
  }

  public ZeebeIncidentImportIndexHandler getZeebeIncidentImportIndexHandler(
      final Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeIncidentImportIndexHandler.class);
  }

  public ZeebeVariableImportIndexHandler getZeebeVariableImportIndexHandler(
      final Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeVariableImportIndexHandler.class);
  }

  public ZeebeUserTaskImportIndexHandler getZeebeUserTaskImportIndexHandler(
      final Integer partitionId) {
    return getZeebeImportIndexHandler(partitionId, ZeebeUserTaskImportIndexHandler.class);
  }

  public ExternalVariableUpdateImportIndexHandler getExternalVariableUpdateImportIndexHandler() {
    return ingestedImportIndexHandlerProvider.getExternalVariableUpdateImportIndexHandler();
  }

  public void reloadConfiguration() {
    ingestedImportIndexHandlerProvider = null;
    engineImportIndexHandlerProviderMap = new HashMap<>();
    zeebeImportIndexHandlerProviderMap = new HashMap<>();
  }

  private <T extends EngineImportIndexHandler> List<T> getEngineHandlers(
      final String engineAlias,
      final Function<EngineImportIndexHandlerProvider, List<T>> getHandlerFunction) {
    return Optional.ofNullable(engineImportIndexHandlerProviderMap.get(engineAlias))
        .map(getHandlerFunction)
        .orElse(null);
  }

  private <T extends EngineImportIndexHandler> T getEngineImportIndexHandler(
      final String engineAlias, final Class<T> handlerClass) {
    return Optional.ofNullable(engineImportIndexHandlerProviderMap.get(engineAlias))
        .map(provider -> provider.getImportIndexHandler(handlerClass))
        .orElse(null);
  }

  private <T extends ZeebeImportIndexHandler> T getZeebeImportIndexHandler(
      final Integer partitionId, final Class<T> handlerClass) {
    return Optional.ofNullable(zeebeImportIndexHandlerProviderMap.get(partitionId))
        .map(provider -> provider.getImportIndexHandler(handlerClass))
        .orElse(null);
  }
}

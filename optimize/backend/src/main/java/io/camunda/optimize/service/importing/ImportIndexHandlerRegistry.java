/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.service.importing.ingested.handler.ExternalVariableUpdateImportIndexHandler;
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
import org.springframework.stereotype.Component;

@Component
public class ImportIndexHandlerRegistry {

  private IngestedImportIndexHandlerProvider ingestedImportIndexHandlerProvider = null;
  private Map<Integer, ZeebeImportIndexHandlerProvider> zeebeImportIndexHandlerProviderMap =
      new HashMap<>();

  public ImportIndexHandlerRegistry() {}

  public void register(
      final IngestedImportIndexHandlerProvider ingestedImportIndexHandlerProvider) {
    this.ingestedImportIndexHandlerProvider = ingestedImportIndexHandlerProvider;
  }

  public void register(
      final int partitionId,
      final ZeebeImportIndexHandlerProvider zeebeImportIndexHandlerProvider) {
    zeebeImportIndexHandlerProviderMap.put(partitionId, zeebeImportIndexHandlerProvider);
  }

  public List<PositionBasedImportIndexHandler> getPositionBasedHandlers(final Integer partitionId) {
    return Optional.ofNullable(zeebeImportIndexHandlerProviderMap.get(partitionId))
        .map(ZeebeImportIndexHandlerProvider::getPositionBasedImportHandlers)
        .orElse(Collections.emptyList());
  }

  public Collection<ImportIndexHandler<?, ?>> getAllIngestedImportHandlers() {
    return ingestedImportIndexHandlerProvider.getAllHandlers();
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
    zeebeImportIndexHandlerProviderMap = new HashMap<>();
  }

  private <T extends ZeebeImportIndexHandler> T getZeebeImportIndexHandler(
      final Integer partitionId, final Class<T> handlerClass) {
    return Optional.ofNullable(zeebeImportIndexHandlerProviderMap.get(partitionId))
        .map(provider -> provider.getImportIndexHandler(handlerClass))
        .orElse(null);
  }
}

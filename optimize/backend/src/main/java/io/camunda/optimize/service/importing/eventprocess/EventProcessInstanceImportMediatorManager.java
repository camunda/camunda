/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.eventprocess;

import io.camunda.optimize.dto.optimize.query.event.process.EventProcessEventDto;
import io.camunda.optimize.dto.optimize.query.event.process.EventProcessPublishStateDto;
import io.camunda.optimize.service.db.EventProcessInstanceIndexManager;
import io.camunda.optimize.service.importing.BackoffImportMediator;
import io.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediator;
import io.camunda.optimize.service.importing.eventprocess.mediator.EventProcessInstanceImportMediatorFactory;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventProcessInstanceImportMediatorManager implements ConfigurationReloadable {

  private final EventProcessInstanceIndexManager eventBasedProcessIndexManager;
  private final EventProcessInstanceImportMediatorFactory mediatorFactory;

  private final Map<String, List<EventProcessInstanceImportMediator<EventProcessEventDto>>>
      importMediators = new ConcurrentHashMap<>();

  public Collection<EventProcessInstanceImportMediator<EventProcessEventDto>> getActiveMediators() {
    return importMediators.values().stream().flatMap(Collection::stream).toList();
  }

  public synchronized void refreshMediators() {
    final Map<String, EventProcessPublishStateDto> availableInstanceIndices =
        eventBasedProcessIndexManager.getPublishedInstanceStatesMap();

    final List<String> removedPublishedIds =
        importMediators.keySet().stream()
            .filter(publishedStateId -> !availableInstanceIndices.containsKey(publishedStateId))
            .toList();
    removedPublishedIds.forEach(
        publishedStateId -> {
          final List<EventProcessInstanceImportMediator<EventProcessEventDto>>
              eventProcessInstanceImportMediators = importMediators.get(publishedStateId);
          eventProcessInstanceImportMediators.forEach(EventProcessInstanceImportMediator::shutdown);
          importMediators.remove(publishedStateId);
        });

    availableInstanceIndices.values().stream()
        .filter(publishState -> !importMediators.containsKey(publishState.getId()))
        .forEach(
            publishState ->
                importMediators.put(
                    publishState.getId(),
                    mediatorFactory.createEventProcessInstanceMediators(publishState)));
  }

  @Override
  public synchronized void reloadConfiguration(final ApplicationContext context) {
    importMediators.values().stream()
        .flatMap(Collection::stream)
        .forEach(BackoffImportMediator::shutdown);
    importMediators.clear();
  }
}

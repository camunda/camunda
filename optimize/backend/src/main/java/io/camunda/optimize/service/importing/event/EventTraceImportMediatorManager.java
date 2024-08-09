/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event;

import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.service.importing.BackoffImportMediator;
import io.camunda.optimize.service.importing.event.mediator.EventTraceImportMediator;
import io.camunda.optimize.service.importing.event.mediator.EventTraceImportMediatorFactory;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
public class EventTraceImportMediatorManager implements ConfigurationReloadable {

  private final EventTraceImportMediatorFactory eventTraceImportMediatorFactory;

  private final Map<String, EventTraceImportMediator> mediators = new ConcurrentHashMap<>();

  @SneakyThrows
  public List<EventTraceImportMediator> getEventTraceImportMediators() {
    refreshMediators();
    return ImmutableList.copyOf(mediators.values());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    mediators.values().forEach(BackoffImportMediator::shutdown);
    mediators.clear();
  }

  private void refreshMediators() {
    mediators.computeIfAbsent(
        EXTERNAL_EVENTS_INDEX_SUFFIX,
        key -> eventTraceImportMediatorFactory.createExternalEventTraceImportMediator());
  }
}

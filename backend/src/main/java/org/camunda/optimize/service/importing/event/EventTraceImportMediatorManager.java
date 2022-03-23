/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event;

import com.google.common.collect.ImmutableList;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.camunda.optimize.service.es.reader.CamundaActivityEventReader;
import org.camunda.optimize.service.importing.BackoffImportMediator;
import org.camunda.optimize.service.importing.event.mediator.EventTraceImportMediator;
import org.camunda.optimize.service.importing.event.mediator.EventTraceImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@AllArgsConstructor
@Component
public class EventTraceImportMediatorManager implements ConfigurationReloadable {

  private final EventTraceImportMediatorFactory eventTraceImportMediatorFactory;
  private final CamundaActivityEventReader camundaActivityEventReader;

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
      key -> eventTraceImportMediatorFactory.createExternalEventTraceImportMediator()
    );

    final Set<String> definitionKeysOfActivityEvents =
      camundaActivityEventReader.getIndexSuffixesForCurrentActivityIndices();
    definitionKeysOfActivityEvents
      .stream()
      .filter(definitionKey -> !mediators.containsKey(definitionKey))
      .forEach(definitionKey -> mediators.put(
        definitionKey, eventTraceImportMediatorFactory.createCamundaEventTraceImportMediator(definitionKey)
      ));
  }
}

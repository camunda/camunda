/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.factory.AbstractImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.websocket.StatusNotifier;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EngineImportSchedulerManagerService implements ConfigurationReloadable {
  private final EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  private final BeanFactory beanFactory;
  private final EngineContextFactory engineContextFactory;
  private final ConfigurationService configurationService;
  private final List<AbstractImportMediatorFactory> mediatorFactories;

  @Getter
  private List<EngineImportScheduler> importSchedulers = new ArrayList<>();

  public EngineImportSchedulerManagerService(final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                             final BeanFactory beanFactory,
                                             final EngineContextFactory engineContextFactory,
                                             final ConfigurationService configurationService,
                                             final List<AbstractImportMediatorFactory> mediatorFactories) {
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.beanFactory = beanFactory;
    this.engineContextFactory = engineContextFactory;
    this.configurationService = configurationService;
    this.mediatorFactories = mediatorFactories;
    initSchedulers();
  }

  @PreDestroy
  public synchronized void shutdown() {
    for (EngineImportScheduler oldScheduler : importSchedulers) {
      oldScheduler.stopImportScheduling();
      oldScheduler.shutdown();
    }
  }

  public synchronized void startSchedulers() {
    for (EngineImportScheduler scheduler : importSchedulers) {
      if (configurationService.isEngineImportEnabled(scheduler.getEngineAlias())) {
        scheduler.startImportScheduling();
      } else {
        log.info("Engine import was disabled by config for engine with alias {}.", scheduler.getEngineAlias());
      }
    }
  }

  public synchronized void stopSchedulers() {
    for (EngineImportScheduler scheduler : importSchedulers) {
      scheduler.stopImportScheduling();
    }
  }

  @Override
  public synchronized void reloadConfiguration(ApplicationContext context) {
    shutdown();
    importIndexHandlerRegistry.reloadConfiguration();
    initSchedulers();
  }

  public void subscribeImportObserver(final StatusNotifier job) {
    importSchedulers.forEach(scheduler -> scheduler.subscribe(job));
  }

  public void unsubscribeImportObserver(final StatusNotifier job) {
    importSchedulers.forEach(scheduler -> scheduler.unsubscribe(job));
  }

  public Map<String, Boolean> getImportStatusMap() {
    return importSchedulers
      .stream()
      .collect(Collectors.toMap(EngineImportScheduler::getEngineAlias, EngineImportScheduler::isImporting));
  }

  private synchronized void initSchedulers() {
    final List<EngineImportScheduler> result = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        final List<EngineImportMediator> mediators = createMediatorList(engineContext);
        final EngineImportScheduler scheduler = new EngineImportScheduler(mediators, engineContext.getEngineAlias());
        result.add(scheduler);
      } catch (Exception e) {
        log.error("Can't create scheduler for engine [{}]", engineContext.getEngineAlias(), e);
      }
    }
    importSchedulers = result;
  }

  private List<EngineImportMediator> createMediatorList(EngineContext engineContext) {
    final List<EngineImportMediator> mediators = new ArrayList<>();
    importIndexHandlerRegistry.register(
      engineContext.getEngineAlias(),
      beanFactory.getBean(EngineImportIndexHandlerProvider.class, engineContext)
    );

    mediatorFactories.stream()
      .map(factory -> factory.createMediators(engineContext))
      .forEach(mediators::addAll);

    mediators.sort(Comparator.comparing(EngineImportMediator::getRank));

    return mediators;
  }
}

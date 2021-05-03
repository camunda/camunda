/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DataImportSourceDto;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.factory.AbstractEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import org.camunda.optimize.service.importing.zeebe.mediator.factory.AbstractZeebeImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ZeebeConfiguration;
import org.camunda.optimize.websocket.StatusNotifier;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ImportSchedulerManagerService implements ConfigurationReloadable {
  private final EngineImportIndexHandlerRegistry importIndexHandlerRegistry;
  private final BeanFactory beanFactory;
  private final EngineContextFactory engineContextFactory;
  private final ConfigurationService configurationService;
  private final List<AbstractEngineImportMediatorFactory> engineMediatorFactories;
  private final List<AbstractZeebeImportMediatorFactory> zeebeMediatorFactories;

  private List<AbstractImportScheduler> importSchedulers = new ArrayList<>();

  public ImportSchedulerManagerService(final EngineImportIndexHandlerRegistry importIndexHandlerRegistry,
                                       final BeanFactory beanFactory,
                                       final EngineContextFactory engineContextFactory,
                                       final ConfigurationService configurationService,
                                       final List<AbstractEngineImportMediatorFactory> engineMediatorFactories,
                                       final List<AbstractZeebeImportMediatorFactory> zeebeMediatorFactories) {
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.beanFactory = beanFactory;
    this.engineContextFactory = engineContextFactory;
    this.configurationService = configurationService;
    this.engineMediatorFactories = engineMediatorFactories;
    this.zeebeMediatorFactories = zeebeMediatorFactories;
    initSchedulers();
  }

  @PreDestroy
  public synchronized void shutdown() {
    for (AbstractImportScheduler oldScheduler : importSchedulers) {
      oldScheduler.stopImportScheduling();
      oldScheduler.shutdown();
    }
  }

  public synchronized void startSchedulers() {
    for (AbstractImportScheduler scheduler : importSchedulers) {
      if (configurationService.isImportEnabled(scheduler.getDataImportSourceDto())) {
        scheduler.startImportScheduling();
      } else {
        log.info(
          "Engine import was disabled by config for import source {}.", scheduler.getDataImportSourceDto()
        );
      }
    }
  }

  public synchronized void stopSchedulers() {
    for (AbstractImportScheduler scheduler : importSchedulers) {
      scheduler.stopImportScheduling();
    }
  }

  @Override
  public synchronized void reloadConfiguration(ApplicationContext context) {
    shutdown();
    importIndexHandlerRegistry.reloadConfiguration();
    initSchedulers();
  }

  public List<EngineImportScheduler> getEngineImportSchedulers() {
    return importSchedulers
      .stream()
      .filter(EngineImportScheduler.class::isInstance)
      .map(EngineImportScheduler.class::cast)
      .collect(Collectors.toList());
  }

  public void subscribeImportObserver(final StatusNotifier job) {
    getEngineImportSchedulers().forEach(scheduler -> scheduler.subscribe(job));
  }

  public void unsubscribeImportObserver(final StatusNotifier job) {
    getEngineImportSchedulers().forEach(scheduler -> scheduler.unsubscribe(job));
  }

  public Map<String, Boolean> getImportStatusMap() {
    return getEngineImportSchedulers()
      .stream()
      .collect(Collectors.toMap(EngineImportScheduler::getEngineAlias, EngineImportScheduler::isImporting));
  }

  private synchronized void initSchedulers() {
    final List<AbstractImportScheduler> schedulers = new ArrayList<>();
    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        final List<ImportMediator> mediators = createEngineMediatorList(engineContext);
        final EngineImportScheduler scheduler = new EngineImportScheduler(
          mediators,
          new DataImportSourceDto(DataImportSourceType.ENGINE, engineContext.getEngineAlias())
        );
        schedulers.add(scheduler);
      } catch (Exception e) {
        log.error("Can't create scheduler for engine [{}]", engineContext.getEngineAlias(), e);
      }
    }

    final ZeebeConfiguration configuredZeebe = configurationService.getConfiguredZeebe();
    if (configuredZeebe.isEnabled()) {
      final List<ImportMediator> zeebeMediatorList = new ArrayList<>();
      // We create a separate mediator for each Zeebe partition configured
      for (int partitionId = 1; partitionId <= configuredZeebe.getPartitionCount(); partitionId ++) {
        zeebeMediatorList.addAll(createZeebeMediatorList(configuredZeebe.getName(), partitionId));
      }
      ZeebeImportScheduler zeebeImportScheduler = new ZeebeImportScheduler(
        zeebeMediatorList,
        new DataImportSourceDto(DataImportSourceType.ZEEBE, configuredZeebe.getName())
      );
      schedulers.add(zeebeImportScheduler);
    }
    importSchedulers = schedulers;
  }

  private List<ImportMediator> createEngineMediatorList(EngineContext engineContext) {
    final List<ImportMediator> mediators = new ArrayList<>();
    importIndexHandlerRegistry.register(
      engineContext.getEngineAlias(),
      beanFactory.getBean(EngineImportIndexHandlerProvider.class, engineContext)
    );

    engineMediatorFactories.stream()
      .map(factory -> factory.createMediators(engineContext))
      .forEach(mediators::addAll);

    mediators.sort(Comparator.comparing(ImportMediator::getRank));

    return mediators;
  }

  private List<ImportMediator> createZeebeMediatorList(final String zeebeName, final int partitionId) {
    log.warn("==============CREATE MEDIATORS HERE=================");
    log.warn("zeebeName: {}, partitionId: {}", zeebeName, partitionId);
    log.warn("==============CREATE MEDIATORS HERE=================");
    return Collections.emptyList();
  }

}

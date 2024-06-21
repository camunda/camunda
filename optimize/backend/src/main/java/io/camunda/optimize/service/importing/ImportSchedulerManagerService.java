/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

import io.camunda.optimize.dto.optimize.SchedulerConfig;
import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.rest.engine.EngineContextFactory;
import io.camunda.optimize.service.importing.engine.EngineImportScheduler;
import io.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.engine.mediator.factory.AbstractEngineImportMediatorFactory;
import io.camunda.optimize.service.importing.ingested.IngestedDataImportScheduler;
import io.camunda.optimize.service.importing.ingested.handler.IngestedImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.ingested.mediator.factory.AbstractIngestedImportMediatorFactory;
import io.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.zeebe.mediator.factory.AbstractZeebeImportMediatorFactory;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ZeebeConfiguration;
import io.camunda.optimize.websocket.StatusNotifier;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ImportSchedulerManagerService implements ConfigurationReloadable {

  private final ImportIndexHandlerRegistry importIndexHandlerRegistry;
  private final BeanFactory beanFactory;
  private final EngineContextFactory engineContextFactory;
  private final ConfigurationService configurationService;
  private final List<AbstractIngestedImportMediatorFactory> ingestedMediatorFactories;
  private final List<AbstractEngineImportMediatorFactory> engineMediatorFactories;
  private final List<AbstractZeebeImportMediatorFactory> zeebeMediatorFactories;

  @Autowired
  private Environment environment;

  @Getter
  private List<AbstractImportScheduler<? extends SchedulerConfig>> importSchedulers =
      new ArrayList<>();

  public ImportSchedulerManagerService(
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final BeanFactory beanFactory,
      final EngineContextFactory engineContextFactory,
      final ConfigurationService configurationService,
      final List<AbstractIngestedImportMediatorFactory> ingestedMediatorFactories,
      final List<AbstractEngineImportMediatorFactory> engineMediatorFactories,
      final List<AbstractZeebeImportMediatorFactory> zeebeMediatorFactories) {
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.beanFactory = beanFactory;
    this.engineContextFactory = engineContextFactory;
    this.configurationService = configurationService;
    this.ingestedMediatorFactories = ingestedMediatorFactories;
    this.engineMediatorFactories = engineMediatorFactories;
    this.zeebeMediatorFactories = zeebeMediatorFactories;
    initSchedulers();
  }

  @PreDestroy
  public synchronized void shutdown() {
    for (final AbstractImportScheduler<? extends SchedulerConfig> oldScheduler : importSchedulers) {
      oldScheduler.stopImportScheduling();
      oldScheduler.shutdown();
    }
  }

  @PostConstruct
  public void init() {
    if (!(Boolean.parseBoolean(environment.getProperty(INTEGRATION_TESTS)))) {
      startSchedulers();
    }
  }

  public synchronized void startSchedulers() {
    for (final AbstractImportScheduler<? extends SchedulerConfig> scheduler : importSchedulers) {
      if (configurationService.isImportEnabled(scheduler.getDataImportSourceDto())) {
        scheduler.startImportScheduling();
      } else {
        log.info(
            "Import was disabled by config for import source {}.",
            scheduler.getDataImportSourceDto());
      }
    }
  }

  public synchronized void stopSchedulers() {
    for (final AbstractImportScheduler<? extends SchedulerConfig> scheduler : importSchedulers) {
      scheduler.stopImportScheduling();
    }
  }

  @Override
  public synchronized void reloadConfiguration(final ApplicationContext context) {
    shutdown();
    importIndexHandlerRegistry.reloadConfiguration();
    initSchedulers();
  }

  public List<EngineImportScheduler> getEngineImportSchedulers() {
    return importSchedulers.stream()
        .filter(EngineImportScheduler.class::isInstance)
        .map(EngineImportScheduler.class::cast)
        .collect(Collectors.toList());
  }

  public Optional<IngestedDataImportScheduler> getIngestedDataImportScheduler() {
    final List<IngestedDataImportScheduler> ingestedDataImportSchedulers =
        importSchedulers.stream()
            .filter(IngestedDataImportScheduler.class::isInstance)
            .map(IngestedDataImportScheduler.class::cast)
            .collect(Collectors.toList());
    if (ingestedDataImportSchedulers.size() > 1) {
      throw new IllegalStateException(
          "There should only be a single Ingested Data Import Scheduler");
    }
    return ingestedDataImportSchedulers.stream().findFirst();
  }

  public Optional<ZeebeImportScheduler> getZeebeImportScheduler() {
    final List<ZeebeImportScheduler> zeebeSchedulers =
        importSchedulers.stream()
            .filter(ZeebeImportScheduler.class::isInstance)
            .map(ZeebeImportScheduler.class::cast)
            .collect(Collectors.toList());
    if (zeebeSchedulers.size() > 1) {
      throw new IllegalStateException("There should only be a single Zeebe Import Scheduler");
    }
    return zeebeSchedulers.stream().findFirst();
  }

  public void subscribeImportObserver(final StatusNotifier job) {
    getEngineImportSchedulers().forEach(scheduler -> scheduler.subscribe(job));
  }

  public void unsubscribeImportObserver(final StatusNotifier job) {
    getEngineImportSchedulers().forEach(scheduler -> scheduler.unsubscribe(job));
  }

  public Map<String, Boolean> getImportStatusMap() {
    return getEngineImportSchedulers().stream()
        .collect(
            Collectors.toMap(
                EngineImportScheduler::getEngineAlias, EngineImportScheduler::isImporting));
  }

  private synchronized void initSchedulers() {
    final List<AbstractImportScheduler<? extends SchedulerConfig>> schedulers = new ArrayList<>();
    schedulers.add(new IngestedDataImportScheduler(createIngestedDataMediatorList()));

    for (final EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        final List<ImportMediator> mediators = createEngineMediatorList(engineContext);
        final EngineImportScheduler scheduler =
            new EngineImportScheduler(
                mediators, new EngineDataSourceDto(engineContext.getEngineAlias()));
        schedulers.add(scheduler);
      } catch (final Exception e) {
        log.error("Can't create scheduler for engine [{}]", engineContext.getEngineAlias(), e);
      }
    }

    final ZeebeConfiguration zeebeConfig = configurationService.getConfiguredZeebe();
    if (zeebeConfig.isEnabled()) {
      final List<ImportMediator> zeebeMediatorList = new ArrayList<>();
      // We create a separate mediator for each Zeebe partition configured
      for (int partitionId = 1; partitionId <= zeebeConfig.getPartitionCount(); partitionId++) {
        zeebeMediatorList.addAll(
            createZeebeMediatorList(new ZeebeDataSourceDto(zeebeConfig.getName(), partitionId)));
      }
      final ZeebeImportScheduler zeebeImportScheduler =
          new ZeebeImportScheduler(
              zeebeMediatorList,
              new ZeebeConfigDto(zeebeConfig.getName(), zeebeConfig.getPartitionCount()));
      schedulers.add(zeebeImportScheduler);
    }
    importSchedulers = schedulers;
  }

  private List<ImportMediator> createIngestedDataMediatorList() {
    importIndexHandlerRegistry.register(
        beanFactory.getBean(IngestedImportIndexHandlerProvider.class));

    return ingestedMediatorFactories.stream()
        .map(AbstractIngestedImportMediatorFactory::createMediators)
        .flatMap(Collection::stream)
        .sorted(Comparator.comparing(ImportMediator::getRank))
        .collect(Collectors.toList());
  }

  private List<ImportMediator> createEngineMediatorList(final EngineContext engineContext) {
    importIndexHandlerRegistry.register(
        engineContext.getEngineAlias(),
        beanFactory.getBean(EngineImportIndexHandlerProvider.class, engineContext));

    return engineMediatorFactories.stream()
        .map(factory -> factory.createMediators(engineContext))
        .flatMap(Collection::stream)
        .sorted(Comparator.comparing(ImportMediator::getRank))
        .collect(Collectors.toList());
  }

  private List<ImportMediator> createZeebeMediatorList(
      final ZeebeDataSourceDto zeebeDataSourceDto) {
    importIndexHandlerRegistry.register(
        zeebeDataSourceDto.getPartitionId(),
        beanFactory.getBean(ZeebeImportIndexHandlerProvider.class, zeebeDataSourceDto));

    return zeebeMediatorFactories.stream()
        .map(factory -> factory.createMediators(zeebeDataSourceDto))
        .flatMap(Collection::stream)
        .sorted(Comparator.comparing(ImportMediator::getRank))
        .collect(Collectors.toList());
  }
}

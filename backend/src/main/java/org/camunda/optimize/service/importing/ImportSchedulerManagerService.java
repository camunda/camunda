/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.SchedulerConfig;
import org.camunda.optimize.dto.optimize.ZeebeConfigDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.engine.mediator.factory.AbstractEngineImportMediatorFactory;
import org.camunda.optimize.service.importing.ingested.IngestedDataImportScheduler;
import org.camunda.optimize.service.importing.ingested.handler.IngestedImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.ingested.mediator.factory.AbstractIngestedImportMediatorFactory;
import org.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import org.camunda.optimize.service.importing.zeebe.handler.ZeebeImportIndexHandlerProvider;
import org.camunda.optimize.service.importing.zeebe.mediator.factory.AbstractZeebeImportMediatorFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ZeebeConfiguration;
import org.camunda.optimize.websocket.StatusNotifier;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

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
  private List<AbstractImportScheduler<? extends SchedulerConfig>> importSchedulers = new ArrayList<>();

  public ImportSchedulerManagerService(final ImportIndexHandlerRegistry importIndexHandlerRegistry,
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
    for (AbstractImportScheduler<? extends SchedulerConfig> oldScheduler : importSchedulers) {
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
    for (AbstractImportScheduler<? extends SchedulerConfig> scheduler : importSchedulers) {
      if (configurationService.isImportEnabled(scheduler.getDataImportSourceDto())) {
        scheduler.startImportScheduling();
      } else {
        log.info("Import was disabled by config for import source {}.", scheduler.getDataImportSourceDto());
      }
    }
  }

  public synchronized void stopSchedulers() {
    for (AbstractImportScheduler<? extends SchedulerConfig> scheduler : importSchedulers) {
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

  public Optional<IngestedDataImportScheduler> getIngestedDataImportScheduler() {
    final List<IngestedDataImportScheduler> ingestedDataImportSchedulers = importSchedulers
      .stream()
      .filter(IngestedDataImportScheduler.class::isInstance)
      .map(IngestedDataImportScheduler.class::cast)
      .collect(Collectors.toList());
    if (ingestedDataImportSchedulers.size() > 1) {
      throw new IllegalStateException("There should only be a single Ingested Data Import Scheduler");
    }
    return ingestedDataImportSchedulers.stream().findFirst();
  }

  public Optional<ZeebeImportScheduler> getZeebeImportScheduler() {
    final List<ZeebeImportScheduler> zeebeSchedulers = importSchedulers
      .stream()
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
    return getEngineImportSchedulers()
      .stream()
      .collect(Collectors.toMap(EngineImportScheduler::getEngineAlias, EngineImportScheduler::isImporting));
  }

  private synchronized void initSchedulers() {
    final List<AbstractImportScheduler<? extends SchedulerConfig>> schedulers = new ArrayList<>();
    schedulers.add(new IngestedDataImportScheduler(createIngestedDataMediatorList()));

    for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
      try {
        final List<ImportMediator> mediators = createEngineMediatorList(engineContext);
        final EngineImportScheduler scheduler = new EngineImportScheduler(
          mediators, new EngineDataSourceDto(engineContext.getEngineAlias())
        );
        schedulers.add(scheduler);
      } catch (Exception e) {
        log.error("Can't create scheduler for engine [{}]", engineContext.getEngineAlias(), e);
      }
    }

    final ZeebeConfiguration zeebeConfig = configurationService.getConfiguredZeebe();
    if (zeebeConfig.isEnabled()) {
      final List<ImportMediator> zeebeMediatorList = new ArrayList<>();
      // We create a separate mediator for each Zeebe partition configured
      for (int partitionId = 1; partitionId <= zeebeConfig.getPartitionCount(); partitionId++) {
        zeebeMediatorList.addAll(createZeebeMediatorList(new ZeebeDataSourceDto(zeebeConfig.getName(), partitionId)));
      }
      ZeebeImportScheduler zeebeImportScheduler = new ZeebeImportScheduler(
        zeebeMediatorList, new ZeebeConfigDto(zeebeConfig.getName(), zeebeConfig.getPartitionCount())
      );
      schedulers.add(zeebeImportScheduler);
    }
    importSchedulers = schedulers;
  }

  private List<ImportMediator> createIngestedDataMediatorList() {
    importIndexHandlerRegistry.register(beanFactory.getBean(IngestedImportIndexHandlerProvider.class));

    return ingestedMediatorFactories.stream()
      .map(AbstractIngestedImportMediatorFactory::createMediators)
      .flatMap(Collection::stream)
      .sorted(Comparator.comparing(ImportMediator::getRank))
      .collect(Collectors.toList());
  }

  private List<ImportMediator> createEngineMediatorList(EngineContext engineContext) {
    importIndexHandlerRegistry.register(
      engineContext.getEngineAlias(),
      beanFactory.getBean(EngineImportIndexHandlerProvider.class, engineContext)
    );

    return engineMediatorFactories.stream()
      .map(factory -> factory.createMediators(engineContext))
      .flatMap(Collection::stream)
      .sorted(Comparator.comparing(ImportMediator::getRank))
      .collect(Collectors.toList());
  }

  private List<ImportMediator> createZeebeMediatorList(final ZeebeDataSourceDto zeebeDataSourceDto) {
    importIndexHandlerRegistry.register(
      zeebeDataSourceDto.getPartitionId(),
      beanFactory.getBean(ZeebeImportIndexHandlerProvider.class, zeebeDataSourceDto)
    );

    return zeebeMediatorFactories.stream()
      .map(factory -> factory.createMediators(zeebeDataSourceDto))
      .flatMap(Collection::stream)
      .sorted(Comparator.comparing(ImportMediator::getRank))
      .collect(Collectors.toList());
  }

}

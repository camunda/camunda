/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.camunda.optimize.dto.optimize.SchedulerConfig;
import io.camunda.optimize.dto.optimize.ZeebeConfigDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.importing.ingested.IngestedDataImportScheduler;
import io.camunda.optimize.service.importing.ingested.handler.IngestedImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.ingested.mediator.factory.AbstractIngestedImportMediatorFactory;
import io.camunda.optimize.service.importing.zeebe.ZeebeImportScheduler;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeImportIndexHandlerProvider;
import io.camunda.optimize.service.importing.zeebe.mediator.factory.AbstractZeebeImportMediatorFactory;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ZeebeConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ImportSchedulerManagerService implements ConfigurationReloadable {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ImportSchedulerManagerService.class);
  private final ImportIndexHandlerRegistry importIndexHandlerRegistry;
  private final BeanFactory beanFactory;
  private final ConfigurationService configurationService;
  private final List<AbstractIngestedImportMediatorFactory> ingestedMediatorFactories;
  private final List<AbstractZeebeImportMediatorFactory> zeebeMediatorFactories;

  @Autowired private Environment environment;

  @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification = "False positives")
  private List<AbstractImportScheduler<? extends SchedulerConfig>> importSchedulers =
      new ArrayList<>();

  public ImportSchedulerManagerService(
      final ImportIndexHandlerRegistry importIndexHandlerRegistry,
      final BeanFactory beanFactory,
      final ConfigurationService configurationService,
      final List<AbstractIngestedImportMediatorFactory> ingestedMediatorFactories,
      final List<AbstractZeebeImportMediatorFactory> zeebeMediatorFactories) {
    this.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.beanFactory = beanFactory;
    this.configurationService = configurationService;
    this.ingestedMediatorFactories = ingestedMediatorFactories;
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
        LOG.info(
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

  private synchronized void initSchedulers() {
    final List<AbstractImportScheduler<? extends SchedulerConfig>> schedulers = new ArrayList<>();
    schedulers.add(new IngestedDataImportScheduler(createIngestedDataMediatorList()));

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

  public List<AbstractImportScheduler<? extends SchedulerConfig>> getImportSchedulers() {
    return importSchedulers;
  }
}

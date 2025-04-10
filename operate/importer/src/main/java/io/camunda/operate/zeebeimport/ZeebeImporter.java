/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Configuration
@DependsOn("searchEngineSchemaInitializer")
public class ZeebeImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeImporter.class);

  @Autowired private OperateProperties operateProperties;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Autowired
  @Qualifier("recordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler recordsReaderThreadPoolExecutor;

  @Autowired private Metrics metrics;

  @PostConstruct
  public void startImportingData() {
    if (operateProperties.getImporter().isStartLoadingDataOnStartup()) {
      scheduleReaders();
    }
  }

  public void scheduleReaders() {
    LOGGER.info("INIT: Start importing data...");
    recordsReaderHolder.getAllRecordsReaders().stream()
        .forEach(recordsReader -> recordsReaderThreadPoolExecutor.submit(recordsReader));
  }

  public void performOneRoundOfImportFor(final Collection<RecordsReader> readers) {
    for (final RecordsReader recordsReader : readers) {
      importOneBatch(recordsReader, false);
    }
  }

  public void performOneRoundOfImport() {
    performOneRoundOfImportFor(recordsReaderHolder.getAllRecordsReaders());
  }

  public void importOneBatch(final RecordsReader recordsReader, final boolean autoContinue) {
    recordsReader.readAndScheduleNextBatch(autoContinue);
  }
}

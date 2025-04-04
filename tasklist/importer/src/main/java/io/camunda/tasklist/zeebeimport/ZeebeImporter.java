/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.property.TasklistProperties;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Set;
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

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Autowired
  @Qualifier("tasklistRecordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler readersExecutor;

  @PostConstruct
  public void startImportingData() {
    if (tasklistProperties.getImporter().isStartLoadingDataOnStartup()) {
      scheduleReaders();
    }
  }

  public void scheduleReaders() {
    final Set<RecordsReader> allRecordsReaders = recordsReaderHolder.getAllRecordsReaders();
    LOGGER.info("INIT: Start importing data by '{}' importers", allRecordsReaders.size());
    allRecordsReaders.forEach(recordsReader -> readersExecutor.submit(recordsReader));
  }

  public int performOneRoundOfImportFor(final Collection<RecordsReader> readers) {
    int countRecords = 0;
    for (final RecordsReader recordsReaderElasticSearch : readers) {
      countRecords += importOneBatch(recordsReaderElasticSearch, false);
    }
    return countRecords;
  }

  public int performOneRoundOfImport() {
    return performOneRoundOfImportFor(recordsReaderHolder.getAllRecordsReaders());
  }

  public int importOneBatch(final RecordsReader recordsReader, final boolean autoContinue) {
    return recordsReader.readAndScheduleNextBatch(autoContinue);
  }
}

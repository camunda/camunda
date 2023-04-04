/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.property.TasklistProperties;
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
@DependsOn("schemaStartup")
public class ZeebeImporter {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeImporter.class);

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @Autowired
  @Qualifier("recordsReaderThreadPoolExecutor")
  private ThreadPoolTaskScheduler readersExecutor;

  @PostConstruct
  public void startImportingData() {
    if (tasklistProperties.getImporter().isStartLoadingDataOnStartup()) {
      scheduleReaders();
    }
  }

  public void scheduleReaders() {
    LOGGER.info("INIT: Start importing data...");
    recordsReaderHolder.getAllRecordsReaders().stream()
        .forEach(recordsReader -> readersExecutor.submit(recordsReader));
  }

  public int performOneRoundOfImportFor(Collection<RecordsReader> readers) {
    int countRecords = 0;
    for (RecordsReader recordsReader : readers) {
      countRecords += importOneBatch(recordsReader, false);
    }
    return countRecords;
  }

  public int performOneRoundOfImport() {
    return performOneRoundOfImportFor(recordsReaderHolder.getAllRecordsReaders());
  }

  public int importOneBatch(RecordsReader recordsReader, final boolean autoContinue) {
    return recordsReader.readAndScheduleNextBatch(autoContinue);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Collection;
import javax.annotation.PostConstruct;
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
      LOGGER.info("INIT: Start importing data...");
      readersExecutor.submit(this::run);
    }
  }

  public void run() {
    try {
      final int countRecords = performOneRoundOfImport();
      if (countRecords == 0) {
        doBackoff();
      } else {
        readersExecutor.submit(this::run);
      }
    } catch (Exception ex) {
      // retry
      LOGGER.error("Error occurred while importing Zeebe data. Will be retried.", ex);
      doBackoff();
    }
  }

  public int performOneRoundOfImportFor(Collection<RecordsReader> readers) throws IOException {
    int countRecords = 0;
    for (RecordsReader recordsReader : readers) {
      countRecords += importOneBatch(recordsReader);
    }
    return countRecords;
  }

  public int performOneRoundOfImport() throws IOException {
    return performOneRoundOfImportFor(recordsReaderHolder.getActiveRecordsReaders());
  }

  public int importOneBatch(RecordsReader recordsReader) throws IOException {
    return recordsReader.readAndScheduleNextBatch();
  }

  private void doBackoff() {
    readersExecutor.schedule(this::run, OffsetDateTime.now().plusSeconds(2).toInstant());
  }
}

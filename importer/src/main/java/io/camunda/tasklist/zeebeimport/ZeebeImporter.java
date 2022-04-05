/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport;

import static io.camunda.tasklist.util.ThreadUtil.sleepFor;

import io.camunda.tasklist.property.TasklistProperties;
import java.io.IOException;
import java.util.Collection;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Configuration
@DependsOn("schemaStartup")
public class ZeebeImporter extends Thread {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeImporter.class);

  private boolean shutdown = false;

  /** Lock object, that can be used to be informed about finished import. */
  private final Object importFinished = new Object();

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

  @PostConstruct
  public void startImportingData() {
    if (tasklistProperties.getImporter().isStartLoadingDataOnStartup()) {
      start();
    }
  }

  @Override
  public void run() {
    LOGGER.info("INIT: Start importing data...");
    while (!shutdown) {
      synchronized (importFinished) {
        try {
          final int countRecords = performOneRoundOfImport();
          if (countRecords == 0) {
            importFinished.notifyAll();
            doBackoff();
          }
        } catch (Exception ex) {
          // retry
          LOGGER.error("Error occurred while importing Zeebe data. Will be retried.", ex);
          doBackoff();
        }
      }
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

  @Bean("importThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(tasklistProperties.getImporter().getThreadsCount());
    executor.setMaxPoolSize(tasklistProperties.getImporter().getThreadsCount());
    executor.setThreadNamePrefix("import_");
    executor.initialize();
    return executor;
  }

  public Object getImportFinished() {
    return importFinished;
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown ZeebeImporter");
    shutdown = true;
    synchronized (importFinished) {
      importFinished.notifyAll();
    }
  }

  private void doBackoff() {
    sleepFor(2000);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport;

import java.io.IOException;
import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import io.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

@Component
@Configuration
@DependsOn("schemaStartup")
public class ZeebeImporter extends Thread {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeImporter.class);

  private boolean shutdown = false;

  /**
   * Lock object, that can be used to be informed about finished import.
   */
  private final Object importFinished = new Object();

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  @PostConstruct
  public void startImportingData() {
    if (operateProperties.getImporter().isStartLoadingDataOnStartup()) {
      start();
    }
  }

  @Override
  public void run() {
    logger.info("INIT: Start importing data...");
    while (!shutdown) {
      synchronized (importFinished) {
        try {
          int countRecords = performOneRoundOfImport();
          if (countRecords == 0) {
            importFinished.notifyAll();
            doBackoff();
          }
        } catch (Exception ex) {
          //retry
          logger.error("Error occurred while importing Zeebe data. Will be retried.", ex);
          doBackoff();
        }
      }
    }
  }

  public int performOneRoundOfImportFor(Collection<RecordsReader> readers) throws IOException {
    int countRecords = 0;
    for (RecordsReader recordsReader: readers) {
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
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(operateProperties.getImporter().getThreadsCount());
    executor.setMaxPoolSize(operateProperties.getImporter().getThreadsCount());
    executor.setThreadNamePrefix("import_");
    executor.initialize();
    return executor;
  }

  public Object getImportFinished() {
    return importFinished;
  }

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown ZeebeImporter");
    shutdown = true;
    synchronized (importFinished) {
      importFinished.notifyAll();
    }
  }

  private void doBackoff() {
    sleepFor(2000);
  }

}

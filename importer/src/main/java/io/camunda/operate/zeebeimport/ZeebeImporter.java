/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import io.camunda.operate.property.OperateProperties;
import java.io.IOException;
import java.util.Collection;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Configuration
@DependsOn("schemaStartup")
public class ZeebeImporter {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeImporter.class);

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebePostImporter zeebePostImporter;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  @PostConstruct
  public void startImportingData() {
    if (operateProperties.getImporter().isStartLoadingDataOnStartup()) {
      scheduleReaders();
      zeebePostImporter.start();
    }
  }

  public void scheduleReaders() {
    logger.info("INIT: Start importing data...");
    recordsReaderHolder.getAllRecordsReaders().stream().forEach(
        recordsReader -> getRecordsReaderTaskExecutor().submit(recordsReader)
    );
  }

  public void performOneRoundOfImportFor(Collection<RecordsReader> readers) throws IOException {
    for (RecordsReader recordsReader: readers) {
      importOneBatch(recordsReader, false);
    }
  }

  public void performOneRoundOfImport() throws IOException {
    performOneRoundOfImportFor(recordsReaderHolder.getAllRecordsReaders());
  }

  public void importOneBatch(RecordsReader recordsReader, boolean autoContinue) {
    recordsReader.readAndScheduleNextBatch(autoContinue);
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

  @Bean("recordsReaderThreadPoolExecutor")
  public ThreadPoolTaskScheduler getRecordsReaderTaskExecutor() {
    ThreadPoolTaskScheduler executor = new ThreadPoolTaskScheduler();
    executor.setPoolSize(operateProperties.getImporter().getReaderThreadsCount());
    executor.setThreadNamePrefix("records_reader_");
    executor.initialize();
    return executor;
  }

}

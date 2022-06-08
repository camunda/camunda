/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtension;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = { INTEGRATION_TESTS + "=true" })
public abstract class AbstractDataCleanupTest {
  protected static final Logger logger = LoggerFactory.getLogger(AbstractDataCleanupTest.class);

  private static final Properties properties = PropertyUtil.loadProperties("static-cleanup-test.properties");

  @Autowired
  protected static ApplicationContext applicationContext;

  @RegisterExtension
  @Order(1)
  protected static ElasticSearchIntegrationTestExtension elasticSearchIntegrationTestExtension =
    new ElasticSearchIntegrationTestExtension();
  @RegisterExtension
  @Order(2)
  protected static EmbeddedOptimizeExtension embeddedOptimizeExtension = new EmbeddedOptimizeExtension();

  protected static long maxCleanupDurationInMin = Long.parseLong(properties.getProperty(
    "cleanup.test.max.duration.in.min",
    "240"
  ));

  static {
    elasticSearchIntegrationTestExtension.disableCleanup();
    embeddedOptimizeExtension.setResetImportOnStart(false);
    embeddedOptimizeExtension.setupOptimize();
  }

  protected static void importEngineData() {
    final OffsetDateTime importStart = OffsetDateTime.now();
    logger.info("Starting import of engine data to Optimize...");
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.importAllEngineData();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    OffsetDateTime afterImport = OffsetDateTime.now();
    long importDurationInMinutes = ChronoUnit.MINUTES.between(importStart, afterImport);
    logger.info("Import took [ " + importDurationInMinutes + " ] min");
  }

  protected static void runCleanupAndAssertFinishedWithinTimeout() throws InterruptedException, TimeoutException {
    logger.info("Starting History Cleanup...");
    final ExecutorService cleanupExecutorService = Executors.newSingleThreadExecutor();
    cleanupExecutorService.execute(
      () -> embeddedOptimizeExtension.getCleanupScheduler().runCleanup()
    );
    cleanupExecutorService.shutdown();
    boolean wasAbleToFinishImportInTime = cleanupExecutorService.awaitTermination(
      maxCleanupDurationInMin, TimeUnit.MINUTES
    );
    logger.info(".. History cleanup finished, timed out {} ", !wasAbleToFinishImportInTime);
    if (!wasAbleToFinishImportInTime) {
      throw new TimeoutException("Import was not able to finish import in " + maxCleanupDurationInMin + " minutes!");
    }
  }

  protected CleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
  }

}

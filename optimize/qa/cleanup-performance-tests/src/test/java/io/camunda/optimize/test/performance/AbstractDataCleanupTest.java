/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.performance;

import static io.camunda.optimize.service.util.configuration.EnvironmentPropertiesConstants.INTEGRATION_TESTS;

import io.camunda.optimize.service.util.configuration.cleanup.CleanupConfiguration;
import io.camunda.optimize.test.it.extension.DatabaseIntegrationTestExtension;
import io.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension;
import io.camunda.optimize.test.util.PropertyUtil;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {INTEGRATION_TESTS + "=true"})
public abstract class AbstractDataCleanupTest {

  protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractDataCleanupTest.class);
  @Autowired
  protected static ApplicationContext applicationContext;
  @RegisterExtension
  @Order(1)
  protected static DatabaseIntegrationTestExtension databaseIntegrationTestExtension =
      new DatabaseIntegrationTestExtension(false);
  @RegisterExtension
  @Order(2)
  protected static EmbeddedOptimizeExtension embeddedOptimizeExtension =
      new EmbeddedOptimizeExtension();

  @SuppressWarnings("checkstyle:constantname")
  private static final Properties properties =
      PropertyUtil.loadProperties("static-cleanup-test.properties");

  protected static long maxCleanupDurationInMin =
      Long.parseLong(properties.getProperty("cleanup.test.max.duration.in.min", "240"));

  @BeforeAll
  public static void beforeAll() {
    embeddedOptimizeExtension.setResetImportOnStart(false);
  }

  protected static void importEngineData() {
    final OffsetDateTime importStart = OffsetDateTime.now();
    LOGGER.info("Starting import of engine data to Optimize...");
    embeddedOptimizeExtension.getDefaultEngineConfiguration().setEventImportEnabled(true);
    embeddedOptimizeExtension.importAllEngineData();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    final OffsetDateTime afterImport = OffsetDateTime.now();
    final long importDurationInMinutes = ChronoUnit.MINUTES.between(importStart, afterImport);
    LOGGER.info("Import took [ " + importDurationInMinutes + " ] min");
  }

  protected static void runCleanupAndAssertFinishedWithinTimeout()
      throws InterruptedException, TimeoutException {
    LOGGER.info("Starting History Cleanup...");
    final ExecutorService cleanupExecutorService = Executors.newSingleThreadExecutor();
    cleanupExecutorService.execute(
        () -> embeddedOptimizeExtension.getCleanupScheduler().runCleanup());
    cleanupExecutorService.shutdown();
    final boolean wasAbleToFinishImportInTime =
        cleanupExecutorService.awaitTermination(maxCleanupDurationInMin, TimeUnit.MINUTES);
    LOGGER.info(".. History cleanup finished, timed out {} ", !wasAbleToFinishImportInTime);
    if (!wasAbleToFinishImportInTime) {
      throw new TimeoutException(
          "Import was not able to finish import in " + maxCleanupDurationInMin + " minutes!");
    }
  }

  protected CleanupConfiguration getCleanupConfiguration() {
    return embeddedOptimizeExtension.getConfigurationService().getCleanupServiceConfiguration();
  }
}

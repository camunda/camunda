/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.ClassRule;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/cleanup-applicationContext.xml"})
public abstract class AbstractCleanupTest {
  protected static final Logger logger = LoggerFactory.getLogger(AbstractCleanupTest.class);

  private static final Properties properties = PropertyUtil.loadProperties("static-cleanup-test.properties");

  protected static ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  protected static EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  protected static EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule(properties);
  protected static long maxCleanupDurationInMin =  Long.parseLong(properties.getProperty("cleanup.test.max.duration.in.min", "240"));

  @ClassRule
  public static RuleChain chain = RuleChain.outerRule(elasticSearchRule)
    .around(embeddedOptimizeRule)
    .around(engineDatabaseRule);

  protected static ConfigurationService getConfigurationService() {
    return embeddedOptimizeRule.getConfigurationService();
  }

  protected static ScheduledExecutorService reportImportProgress() {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    exec.scheduleAtFixedRate(
      () -> {
        logger.info("Progress of engine import: {}%", computeImportProgress());
      },
      0,
      60,
      TimeUnit.SECONDS
    );
    return exec;
  }

  protected static void importData() {
    final OffsetDateTime importStart = OffsetDateTime.now();
    logger.info("Starting import of engine data to Optimize...");
    final ScheduledExecutorService progressReporterExecutorService = reportImportProgress();
    embeddedOptimizeRule.importAllEngineData();
    progressReporterExecutorService.shutdown();
    elasticSearchRule.refreshAllOptimizeIndices();
    OffsetDateTime afterImport = OffsetDateTime.now();
    long importDurationInMinutes = ChronoUnit.MINUTES.between(importStart, afterImport);
    logger.info("Import took [ " + importDurationInMinutes + " ] min");
  }

  protected static void runCleanupAndAssertFinishedWithinTimeout() throws InterruptedException, TimeoutException {
    logger.info("Starting History Cleanup...");
    final ExecutorService cleanupExecutorService = Executors.newSingleThreadExecutor();
    cleanupExecutorService.execute(
      () -> embeddedOptimizeRule.getCleanupScheduler().runCleanup()
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

  private static long computeImportProgress() {
    Integer activityInstancesImported = elasticSearchRule.getActivityCount(getConfigurationService());
    Long totalInstances = null;
    try {
      totalInstances = Math.max(engineDatabaseRule.countHistoricActivityInstances(), 1L);
      return Math.round(activityInstancesImported.doubleValue() / totalInstances.doubleValue() * 100);
    } catch (SQLException e) {
      e.printStackTrace();
      return 0L;
    }
  }

}

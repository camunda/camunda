package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.CleanupMode;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.Test;

import java.time.OffsetDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ImportPerformanceStaticDataTest extends AbstractImportTest {

  @Override
  public Properties getProperties() {
    return PropertyUtil.loadProperties("static-import-test.properties");
  }

  @Test
  public void importPerformanceTest() throws Exception {
    logStats();

    //given I have data in the engine database
    // # requirement setup outside of test scope

    // when I import all data
    final OffsetDateTime importStart = OffsetDateTime.now();
    logger.info("Starting import of engine data to Optimize...");
    importEngineData();
    OffsetDateTime afterImport = OffsetDateTime.now();
    long importDurationInMinutes = ChronoUnit.MINUTES.between(importStart, afterImport);
    logger.info("Import took [ " + importDurationInMinutes + " ] min");

    // then all data from the engine should be in Elasticsearch
    logStats();
    assertThatEngineAndElasticDataMatch();
  }

  @Test
  public void cleanupPerformanceTest() throws Exception {
    logStats();

    //given TTL is 1 month
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P1M"));
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultMode(CleanupMode.ALL);

    // when I import all data
    final OffsetDateTime importStart = OffsetDateTime.now();
    logger.info("Starting import of engine data to Optimize...");
    importEngineData();
    OffsetDateTime afterImport = OffsetDateTime.now();
    long importDurationInMinutes = ChronoUnit.MINUTES.between(importStart, afterImport);
    logger.info("Import took [ " + importDurationInMinutes + " ] min");
    logStats();

    // and start the cleanup
    embeddedOptimizeRule.getCleanupService().runCleanup();
    // and refresh es
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then no process instances should be left in optimize
    assertThat(
      "processInstanceTypeCount",
      getImportedCountOf(configurationService.getProcessInstanceType()),
      is(0)
    );
  }

  private void importEngineData() throws InterruptedException, TimeoutException {
    final ExecutorService importExecutorService = Executors.newSingleThreadExecutor();
    importExecutorService.execute(
      () -> embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities()
    );
    ExecutorService executor = importExecutorService;

    ScheduledExecutorService progressReporterExecutorService = reportImportProgress();
    executor.shutdown();
    boolean wasAbleToFinishImportInTime = executor.awaitTermination(
      maxImportDurationInMin,
      TimeUnit.MINUTES
    );
    if (!wasAbleToFinishImportInTime) {
      throw new TimeoutException("Import was not able to finish import in " + maxImportDurationInMin + " minutes!");
    }
    progressReporterExecutorService.shutdown();

    elasticSearchRule.refreshOptimizeIndexInElasticsearch();
  }

}

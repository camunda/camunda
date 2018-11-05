package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.configuration.CleanupMode;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

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

// fixed ordering for now to save import time, we first test clearing variables, then clear whole instances
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CleanupPerformanceStaticDataTest extends AbstractCleanupTest {
  private boolean imported;

  @Override
  public Properties getProperties() {
    return PropertyUtil.loadProperties("static-cleanup-test.properties");
  }

  @Test
  public void aCleanupModeVariablesPerformanceTest() throws Exception {
    //given TTL of 0
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeRule.getConfigurationService()
      .getCleanupServiceConfiguration()
      .setDefaultMode(CleanupMode.VARIABLES);

    // when I import all data
    importData();
    final int countProcessDefinitions = elasticSearchRule.getImportedCountOf(
      configurationService.getProcessDefinitionType(), configurationService
    );
    final int processInstanceCount = elasticSearchRule.getImportedCountOf(
      configurationService.getProcessInstanceType(), configurationService
    );
    final int activityCount = elasticSearchRule.getActivityCount(configurationService);
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then no variables should be left in optimize
    assertThat(
      "variableInstanceCount",
      elasticSearchRule.getVariableInstanceCount(configurationService),
      is(0)
    );
    // and everything else is untouched
    assertThat(
      "processInstanceTypeCount",
      elasticSearchRule.getImportedCountOf(configurationService.getProcessInstanceType(), configurationService),
      is(processInstanceCount)
    );
    assertThat(
      "activityCount",
      elasticSearchRule.getActivityCount(configurationService),
      is(activityCount)
    );
    assertThat(
      "processDefinitionCount",
      elasticSearchRule.getImportedCountOf(configurationService.getProcessDefinitionType(), configurationService),
      is(countProcessDefinitions)
    );
  }

  @Test
  public void bCleanupModeAllPerformanceTest() throws Exception {
    //given ttl of 0
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultTtl(Period.parse("P0D"));
    embeddedOptimizeRule.getConfigurationService().getCleanupServiceConfiguration().setDefaultMode(CleanupMode.ALL);

    // when I import all data
    importData();
    final int countProcessDefinitions = elasticSearchRule.getImportedCountOf(
      configurationService.getProcessDefinitionType(), configurationService
    );
    // and run the cleanup
    runCleanupAndAssertFinishedWithinTimeout();
    // and refresh es
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    // then no process instances, no activity and no variables should be left in optimize
    assertThat(
      "processInstanceTypeCount",
      elasticSearchRule.getImportedCountOf(configurationService.getProcessInstanceType(), configurationService),
      is(0)
    );
    assertThat(
      "activityCount",
      elasticSearchRule.getActivityCount(configurationService),
      is(0)
    );
    assertThat(
      "variableInstanceCount",
      elasticSearchRule.getVariableInstanceCount(configurationService),
      is(0)
    );
    // and process definition count is untouched
    assertThat(
      "processDefinitionCount",
      elasticSearchRule.getImportedCountOf(configurationService.getProcessDefinitionType(), configurationService),
      is(countProcessDefinitions)
    );

    // set imported to false, any following tests need to reimport
    imported = false;
  }

  private void importData() {
    // import once for all tests
    if (!imported) {
      final OffsetDateTime importStart = OffsetDateTime.now();
      logger.info("Starting import of engine data to Optimize...");
      final ScheduledExecutorService progressReporterExecutorService = reportImportProgress();
      embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
      progressReporterExecutorService.shutdown();
      elasticSearchRule.refreshOptimizeIndexInElasticsearch();
      OffsetDateTime afterImport = OffsetDateTime.now();
      long importDurationInMinutes = ChronoUnit.MINUTES.between(importStart, afterImport);
      logger.info("Import took [ " + importDurationInMinutes + " ] min");
      imported = true;
    } else {
      logger.info("Data was already imported, skipping import");
    }
  }

  private void runCleanupAndAssertFinishedWithinTimeout() throws InterruptedException, TimeoutException {
    logger.info("Starting History Cleanup...");
    final ExecutorService cleanupExecutorService = Executors.newSingleThreadExecutor();
    cleanupExecutorService.execute(
      () -> embeddedOptimizeRule.getCleanupService().runCleanup()
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

}

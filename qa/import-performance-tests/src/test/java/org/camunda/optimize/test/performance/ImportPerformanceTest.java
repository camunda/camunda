package org.camunda.optimize.test.performance;

import org.camunda.optimize.service.util.NamedThreadFactory;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.performance.data.generation.DataGenerator;
import org.camunda.optimize.test.performance.data.generation.DataGeneratorProvider;
import org.camunda.optimize.test.util.PropertyUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/import-performance-applicationContext.xml"})
public class ImportPerformanceTest {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  private EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();


  private static final int QUEUE_SIZE = 100;
  private int NUMBER_OF_INSTANCES;
  private boolean shouldGenerateData;
  private long maxImportDurationInMin;

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
    Properties properties = PropertyUtil.loadProperties("import-performance-test.properties");
    NUMBER_OF_INSTANCES = Integer.parseInt(properties.getProperty("import.test.number.of.processes"));
    shouldGenerateData = Boolean.parseBoolean(properties.getProperty("import.test.generate.data"));
    maxImportDurationInMin = Long.parseLong(properties.getProperty("import.test.max.duration.in.max"));
  }

  @Test
  public void importPerformanceTest() throws Exception {
    //given
    OffsetDateTime pointOne = OffsetDateTime.now();
    if (shouldGenerateData) {
      generateData();
    }
    OffsetDateTime pointTwo = OffsetDateTime.now();
    logger.info("Data generation took [ " + ChronoUnit.MINUTES.between(pointOne, pointTwo) + " ] min");

    //trigger import
    logger.info("Starting import of engine data to Optimize...");
    ScheduledExecutorService progressReporter = reportImportProgress();
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    stopReportingProgress(progressReporter);

    OffsetDateTime pointThree = OffsetDateTime.now();

    //report results
    long importDurationInMinutes = ChronoUnit.MINUTES.between(pointTwo,pointThree);
    logger.info("Import took [ " +  importDurationInMinutes + " ] min");
    assertThat(importDurationInMinutes, lessThanOrEqualTo(maxImportDurationInMin));
  }

  private ScheduledExecutorService reportImportProgress() {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(this.getClass().getSimpleName()));
    exec.scheduleAtFixedRate(
      () -> {
        logger.info("Progress of engine import: {}%",
          embeddedOptimizeRule.getProgressValue());
      }, 0, 5, TimeUnit.MILLISECONDS
    );
    return exec;
  }

  public void generateData() throws InterruptedException {
    BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    ThreadPoolExecutor importExecutor = new ThreadPoolExecutor(
      3, 20, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue, new WaitHandler());

    DataGeneratorProvider dataGeneratorProvider = new DataGeneratorProvider(NUMBER_OF_INSTANCES);
    for (DataGenerator dataGenerator : dataGeneratorProvider.getDataGenerators()) {
      importExecutor.execute(dataGenerator);
    }

    ScheduledExecutorService exec = reportDataGenerationProgress(importJobsQueue, dataGeneratorProvider);

    importExecutor.shutdown();
    importExecutor.awaitTermination(16L, TimeUnit.HOURS);
    logger.info("Finished data generation!");

    stopReportingProgress(exec);
  }

  private void stopReportingProgress(ScheduledExecutorService exec) {
    exec.shutdownNow();
  }

  private ScheduledExecutorService reportDataGenerationProgress(BlockingQueue<Runnable> importJobsQueue,
                                                                DataGeneratorProvider dataGeneratorProvider) {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Integer nGenerators = dataGeneratorProvider.getTotalDataGeneratorCount();
    exec.scheduleAtFixedRate(() -> {
      Integer finishedCount = (nGenerators - importJobsQueue.size());
      double finishedAmountInPercentage = Math.round((finishedCount.doubleValue() / nGenerators.doubleValue() * 100.0));
      logger.info("Progress of data generation: {}%", finishedAmountInPercentage);
    }, 0, 5, TimeUnit.SECONDS);
    return exec;
  }

  private class WaitHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      try {
        executor.getQueue().put(r);
      } catch (InterruptedException e) {
        logger.error("interrupted generation", e);
      }
    }
  }
}

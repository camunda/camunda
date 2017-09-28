package org.camunda.optimize.test.performance;

import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
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

import java.time.LocalDateTime;
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

  public static final int TEN_SECONDS = 10_000;
  public static final int QUEUE_SIZE = 100;
  private final Logger logger = LoggerFactory.getLogger(ImportPerformanceTest.class);
  public EngineIntegrationRule engineRule = new EngineIntegrationRule("import-performance-test.properties");
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  private int NUMBER_OF_INSTANCES;
  private boolean shouldGenerateData;

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  @Before
  public void setUp() {
    Properties properties = PropertyUtil.loadProperties("import-performance-test.properties");
    NUMBER_OF_INSTANCES = Integer.parseInt(properties.getProperty("import.test.number.of.processes"));
    shouldGenerateData = Boolean.parseBoolean(properties.getProperty("import.test.generate.data"));
  }

  @Test
  public void importPerformanceTest() throws Exception {
    //given
    LocalDateTime pointOne = LocalDateTime.now();
    if (shouldGenerateData) {
      generateData();
    }
    LocalDateTime pointTwo = LocalDateTime.now();
    logger.info("Data generation took [ " + ChronoUnit.MINUTES.between(pointOne, pointTwo) + " ] min");

    //trigger import
    logger.info("Starting import of engine data to Optimize...");
    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.startImportScheduler();
    embeddedOptimizeRule.getJobExecutor().startExecutingImportJobs();
    //give importing time to warm up
    Thread.sleep(TEN_SECONDS);
    while (embeddedOptimizeRule.isImporting() || embeddedOptimizeRule.getProgressValue() < 99) {
      Thread.sleep(TEN_SECONDS);
      logger.info("current import progress [" + embeddedOptimizeRule.getProgressValue() + "%]");
    }

    embeddedOptimizeRule.getJobExecutor().stopExecutingImportJobs();

    LocalDateTime pointThree = LocalDateTime.now();

    //report results
    long importDurationInMinutes = ChronoUnit.MINUTES.between(pointTwo,pointThree);
    long fifteenMinutes = 15L;
    logger.info("Import took [ " +  importDurationInMinutes + " ] min");
    assertThat(importDurationInMinutes, lessThanOrEqualTo(fifteenMinutes));
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

    stopReportingDataGenerationProgress(exec);
  }

  private void stopReportingDataGenerationProgress(ScheduledExecutorService exec) {
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

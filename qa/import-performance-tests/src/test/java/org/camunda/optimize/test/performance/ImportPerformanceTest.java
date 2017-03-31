package org.camunda.optimize.test.performance;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.rest.engine.dto.DeploymentDto;
import org.camunda.optimize.test.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.rule.EngineIntegrationRule;
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Askar Akhmerov
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"/import-performance-applicationContext.xml"})
public class ImportPerformanceTest {

  public static final int TEN_SECONDS = 10000;
  private final Logger logger = LoggerFactory.getLogger(ImportPerformanceTest.class);

  private int NUMBER_OF_INSTANCES;
  private boolean generateData;

  public EngineIntegrationRule engineRule = new EngineIntegrationRule("import-performance-test.properties");
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule(true);
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(embeddedOptimizeRule);

  private Properties properties;

  @Before
  public void setUp() {
    properties = PropertyUtil.loadProperties("import-performance-test.properties");
    NUMBER_OF_INSTANCES = Integer.parseInt(properties.getProperty("import.test.number.of.processes"));
    generateData = Boolean.parseBoolean(properties.getProperty("import.test.generate.data"));
  }

  @Test
  public void importPerformanceTest() throws Exception {
    //generate data in te engine
    //given
    LocalDateTime pointOne = LocalDateTime.now();
    if (generateData) {
      deployAndStartSimpleServiceTask(NUMBER_OF_INSTANCES);
    }
    LocalDateTime pointTwo = LocalDateTime.now();
    logger.info("Starting took [ " + ChronoUnit.SECONDS.between(pointOne,pointTwo) + " ] sec");
    //trigger import

    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.startImportScheduler();
    //give importing time to warm up
    Thread.currentThread().sleep(TEN_SECONDS);
    while (embeddedOptimizeRule.isImporting() || embeddedOptimizeRule.getProgressValue() < 99) {
      Thread.currentThread().sleep(TEN_SECONDS);
      logger.info("current import progress [" + embeddedOptimizeRule.getProgressValue() + "%]");
    }

    LocalDateTime pointThree = LocalDateTime.now();

    //report results
    logger.info("Import took [ " + ChronoUnit.SECONDS.between(pointTwo,pointThree) + " ] sec");
  }

  private void deployAndStartSimpleServiceTask(int numberOfInstances) throws IOException, InterruptedException {
    BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(numberOfInstances);
    ThreadPoolExecutor importExecutor = new ThreadPoolExecutor(2, 20, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue);
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
          .startEvent()
            .serviceTask()
              .camundaExpression("${true}")
          .endEvent()
        .done();

    CloseableHttpClient client = HttpClientBuilder.create().build();
    DeploymentDto deployment = engineRule.deployProcess(processModel, client);

    List<ProcessDefinitionEngineDto> procDefs = engineRule.getAllProcessDefinitions(deployment, client);
    client.close();

    assertThat(procDefs.size(), is(1));
    for (int i = 0; i < numberOfInstances; i++) {
      Runnable asyncStart = () -> {
        try {
          CloseableHttpClient threadClient = HttpClientBuilder.create().build();
          engineRule.startProcessInstance(procDefs.get(0).getId(), threadClient);
          threadClient.close();
        } catch (IOException e) {
          logger.error("error while starting process", e);
        }
      };

      importExecutor.execute(asyncStart);
    }
    importExecutor.shutdown();
    boolean finished = importExecutor.awaitTermination(30L, TimeUnit.MINUTES);
    //assertThat("Finished data generation in 10 Minutes", finished, is(true));
  }



}

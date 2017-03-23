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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;
import java.util.List;
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

  private final Logger logger = LoggerFactory.getLogger(ImportPerformanceTest.class);

  private int NUMBER_OF_INSTANCES = 100_000;
  private boolean generateData = true;
  public EngineIntegrationRule engineRule = new EngineIntegrationRule("import-performance-test.properties", false);
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule(false, false);

  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void importPerformanceTest() throws Exception {
    //generate data in te engine
    //given
    long dayOne = System.currentTimeMillis();
    if (generateData) {
      deployAndStartSimpleServiceTask(NUMBER_OF_INSTANCES);
    }
    long dayTwo = System.currentTimeMillis();
    logger.info("Starting took [ " + (dayTwo - dayOne)/1000 + " ] sec");
    //trigger import

    engineRule.waitForAllProcessesToFinish();
    embeddedOptimizeRule.startImportScheduler();
    //give importing time to warm up
    Thread.currentThread().sleep(10000);
    while (embeddedOptimizeRule.isImporting()) {
      Thread.currentThread().sleep(10000);
      logger.info("current import progress [" + embeddedOptimizeRule.getProgressValue() + "%]");
    }

    long dayThree = System.currentTimeMillis();

    //report results
    logger.info("Import took [ " + (dayThree - dayTwo)/1000 + " ] sec");
  }

  private void deployAndStartSimpleServiceTask(int numberOfInstances) throws IOException, InterruptedException {
    BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(NUMBER_OF_INSTANCES);
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
        } catch (IOException e) {
          logger.error("error while starting process", e);
        }
      };

      importExecutor.execute(asyncStart);
    }
    boolean finished = importExecutor.awaitTermination(30L, TimeUnit.MINUTES);
    //assertThat("Finished data generation in 10 Minutes", finished, is(true));
  }



}

package org.camunda.optimize.qa.performance.steps;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.optimize.qa.performance.framework.PerfTestConfiguration;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStep;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newFixedThreadPool;

public abstract class DataGenerationStep extends PerfTestStep {
  
  private Logger logger = LoggerFactory.getLogger(DataGenerationStep.class);

  private final int MAX_BULK_SIZE = 50_000;
  protected Client client = null;
  protected PerfTestContext context;
  private Integer dataGenerationSize;
  private Integer numberOfThreads;
  protected SimpleDateFormat sdf;

  @Override
  public PerfTestStepResult execute(PerfTestContext context) {
    this.context = context;
    loadContext();
    setContextParameter();

    if (!wasDataAlreadyGenerated()) {
      BpmnModelInstance modelInstance = createBpmnModel();
      addModelToElasticsearch(modelInstance);
      addBulkOfDataToElasticsearch(modelInstance, dataGenerationSize);
    }

    // nothing to return
    return new PerfTestStepResult();
  }

  private void setContextParameter() {
    context.addParameter("processDefinitionId", "myDataGenerationProcessDefinitionId");
  }

  private boolean wasDataAlreadyGenerated() {
    client
      .admin()
      .indices()
      .prepareRefresh(context.getConfiguration().getOptimizeIndex())
      .get();

    SearchResponse response = client
      .prepareSearch(context.getConfiguration().getOptimizeIndex())
      .setSize(0)
      .get();
    return response.getHits().getTotalHits() >= dataGenerationSize;
  }

  protected void addModelToElasticsearch(BpmnModelInstance instance) {
  }

  protected abstract BpmnModelInstance createBpmnModel();

  private List<Integer> createBulkSizes(int totalAmountOfEntitiesToAdd) {
    List<Integer> bulkSizes = new LinkedList<>();
    int maxBulkSizeCount = totalAmountOfEntitiesToAdd / MAX_BULK_SIZE;
    int finalBulkSize = totalAmountOfEntitiesToAdd % MAX_BULK_SIZE;
    bulkSizes.addAll(Collections.nCopies(maxBulkSizeCount, MAX_BULK_SIZE));
    if (finalBulkSize > 0) {
      bulkSizes.add(finalBulkSize);
    }
    return bulkSizes;
  }

  private void loadContext() {
    PerfTestConfiguration configuration = context.getConfiguration();
    dataGenerationSize = dataGenerationSize == null ? configuration.getDataGenerationSize() : dataGenerationSize;
    numberOfThreads = configuration.getNumberOfThreads();
    client = context.getConfiguration().getClient();
    sdf = new SimpleDateFormat(context.getConfiguration().getDateFormat());
  }

  private String getProcessDefinitionKey(BpmnModelInstance modelInstance) {
    String result = "";
    for (Process process : modelInstance.getModelElementsByType(Process.class)) {
      result = process.getId();
    }
    return result;
  }

  private void addBulkOfDataToElasticsearch(BpmnModelInstance modelInstance, int totalAmountOfEntitiesToAdd) {
    List<Integer> bulkSizes = createBulkSizes(totalAmountOfEntitiesToAdd);
    String processDefinitionKey = getProcessDefinitionKey(modelInstance);
    ExecutorService executor = newFixedThreadPool(numberOfThreads);
    AtomicInteger processedCount = new AtomicInteger(0);
    for (Integer bulkSize : bulkSizes) {
      executor.execute(() -> {
        BulkRequestBuilder bulkRequest = client.prepareBulk();
        int i = 0;
        while (i < bulkSize) {
          i += addGeneratedData(bulkRequest, modelInstance, processDefinitionKey);
        }
        bulkRequest.get();
        int newProcessedCount = processedCount.addAndGet(bulkSize);
        double progress = ((double)newProcessedCount/totalAmountOfEntitiesToAdd) * 100;
        long roundedProgress = Math.round(progress);
        logger.info("Progress of data generation: " + roundedProgress + " %");
      });

    }
    executor.shutdown();
    try {
      executor.awaitTermination(2, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new PerfTestException("Data generation should not take more than two minutes!", e);
    }
  }

  protected abstract int addGeneratedData(BulkRequestBuilder bulkRequest, BpmnModelInstance modelInstance, String processDefinitionKey);

}

package org.camunda.optimize.qa.performance.steps;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.optimize.qa.performance.framework.PerfTestConfiguration;
import org.camunda.optimize.qa.performance.framework.PerfTestContext;
import org.camunda.optimize.qa.performance.framework.PerfTestStep;
import org.camunda.optimize.qa.performance.framework.PerfTestStepResult;
import org.camunda.optimize.qa.performance.util.IdGenerator;
import org.camunda.optimize.qa.performance.util.PerfTestException;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public abstract class DataGenerationStep extends PerfTestStep {
  
  private Logger logger = LoggerFactory.getLogger(DataGenerationStep.class);

  private final int MAX_BULK_SIZE = 50_000;
  protected TransportClient client = null;
  protected PerfTestContext context;
  private Integer dataGenerationSize;
  private Integer numberOfThreads;
  private SimpleDateFormat sdf;

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

  private int addGeneratedData(BulkRequestBuilder bulkRequest, BpmnModelInstance modelInstance, String processDefinitionKey) {

    Collection<FlowNode> flowNodes = extractFlowNodes(modelInstance);
    String processInstanceId = "processInstance:" + IdGenerator.getNextId();
    for (FlowNode flowNode : flowNodes) {
      String id = IdGenerator.getNextId();
      XContentBuilder source = generateSource(
          id,
          flowNode.getId(),
          processDefinitionKey,
          processInstanceId
      );
      bulkRequest
        .add(client
          .prepareIndex(
              context.getConfiguration().getOptimizeIndex(),
              context.getConfiguration().getEventType(),
              id
          )
          .setSource(source)
        );
    }
    return flowNodes.size();
  }

  protected Collection<FlowNode> extractFlowNodes(BpmnModelInstance instance) {
    return instance.getModelElementsByType(FlowNode.class);
  }

  private XContentBuilder generateSource(String id, String activityId, String processDefinitionKey, String processInstanceId) {
    try {
      String date = sdf.format(new Date());
      return jsonBuilder()
        .startObject()
        .field("id", id)
        .field("activityId", activityId)
        .field("activityInstanceId", generateIdFrom(activityId))
        .field("timestamp", date)
        .field("processDefinitionKey", processDefinitionKey)
        .field("processDefinitionId", context.getParameter("processDefinitionId"))
        .field("processInstanceId", processInstanceId)
        .field("startDate", date)
        .field("endDate", date)
        .field("processInstanceStartDate", date)
        .field("processInstanceEndDate", date)
        .field("durationInMs", 20)
        .field("activityType", "flowNode")
        .endObject();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private String generateIdFrom(String str) {
    return str + ":" + IdGenerator.getNextId();
  }

}

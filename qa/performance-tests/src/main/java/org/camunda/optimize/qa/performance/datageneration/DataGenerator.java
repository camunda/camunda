package org.camunda.optimize.qa.performance.datageneration;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.elasticsearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class DataGenerator {

  private static final String OPTIMIZE_INDEX = "optimize";
  private static final String EVENT_TYPE = "event";
  private static final int MAX_BULK_SIZE = 50_000;
  private static TransportClient client = null;

  public static void main(String[] args) {
    // default size
    int totalAmountOfEntitiesToAdd = 1_000_000;
    if (args.length > 0) {
      totalAmountOfEntitiesToAdd = Integer.valueOf(args[0]);
    }
    initializeElasticsearchClient();
    prepareOptimizeIndex();

    System.out.println("Starting to generate data...");
    long start, end;
    start = System.currentTimeMillis();

    BpmnModelInstance modelInstance = createBpmnModel();
    addBulkOfDataToElasticsearch(modelInstance, totalAmountOfEntitiesToAdd);

    end = System.currentTimeMillis();
    System.out.println("Data generation has been successfully completed.");
    long totalTime = (end - start) / 1000;
    System.out.println("Total time of data generation: " + totalTime + " seconds");
    closeElasticsearchClient();
  }

  private static void closeElasticsearchClient() {
    client.close();
  }

  private static void prepareOptimizeIndex() {
    IndicesExistsResponse response = client.admin().indices().prepareExists(OPTIMIZE_INDEX).get();
    if (response.isExists()) {
      client.admin().indices().prepareDelete(OPTIMIZE_INDEX).get();
    }
    client.admin().indices().prepareCreate(OPTIMIZE_INDEX).get();
    client.admin().indices().prepareRefresh(OPTIMIZE_INDEX).get();
  }

  private static String getProcessDefinitionKey(BpmnModelInstance modelInstance) {
    String result = "";
    for (Process process : modelInstance.getModelElementsByType(Process.class)) {
      result = process.getId();
    }
    return result;
  }

  private static void initializeElasticsearchClient() {
    try {
      client = new PreBuiltTransportClient(Settings.EMPTY)
        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("127.0.0.1"), 9300));
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    client.admin().cluster().prepareHealth(OPTIMIZE_INDEX).setWaitForYellowStatus().get();
  }

  private static BpmnModelInstance createBpmnModel() {
    return Bpmn.createExecutableProcess("aProcess")
      .startEvent()
      .serviceTask()
      .endEvent()
      .done();
  }

  private static void addBulkOfDataToElasticsearch(BpmnModelInstance modelInstance, int totalAmountOfEntitiesToAdd) {
    List<Integer> bulkSizes = createBulkSizes(totalAmountOfEntitiesToAdd);
    for (Integer bulkSize : bulkSizes) {
      BulkRequestBuilder bulkRequest = client.prepareBulk();
      int i = 0;
      while (i < bulkSize) {
        i += addGeneratedData(bulkRequest, modelInstance);
      }
      bulkRequest.get();
    }
  }

  private static List<Integer> createBulkSizes(int totalAmountOfEntitiesToAdd) {
    List<Integer> bulkSizes = new LinkedList<>();
    int maxBulkSizeCount = totalAmountOfEntitiesToAdd / MAX_BULK_SIZE;
    int finalBulkSize = totalAmountOfEntitiesToAdd % MAX_BULK_SIZE;
    bulkSizes.addAll(Collections.nCopies(maxBulkSizeCount, MAX_BULK_SIZE));
    if (finalBulkSize > 0) {
      bulkSizes.add(finalBulkSize);
    }
    return bulkSizes;
  }

  private static int addGeneratedData(BulkRequestBuilder bulkRequest, BpmnModelInstance modelInstance) {
    String processDefinitionKey = getProcessDefinitionKey(modelInstance);
    Collection<FlowNode> flowNodes = modelInstance.getModelElementsByType(FlowNode.class);
    for (FlowNode flowNode : flowNodes) {
      String id = IdGenerator.getNextId();
      XContentBuilder source = generateSource(id, flowNode.getId(), processDefinitionKey, IdGenerator.getNextId());
      bulkRequest.add(client.prepareIndex(OPTIMIZE_INDEX, EVENT_TYPE, id)
        .setSource(source)
      );
    }
    return flowNodes.size();
  }

  private static XContentBuilder generateSource(String id, String activityId, String processDefinitionKey, String processInstanceId) {
    try {
      return jsonBuilder()
        .startObject()
        .field("id", id)
        .field("activityId", activityId)
        .field("activityInstanceId", generateIdFrom(activityId))
        .field("timestamp", new Date())
        .field("processDefinitionKey", processDefinitionKey)
        .field("processDefinitionId", generateIdFrom(processDefinitionKey))
        .field("processInstanceId", processInstanceId)
        .field("startDate", new Date())
        .field("endDate", new Date())
        .field("processInstanceStartDate", new Date())
        .field("processInstanceEndDate", new Date())
        .field("durationInMs", 20)
        .field("activityType", "flowNode")
        .endObject();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private static String generateIdFrom(String str) {
    return str + ":" + IdGenerator.getNextId();
  }

}

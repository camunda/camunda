/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.backup;

import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.operate.webapp.rest.dto.ProcessGroupDto;
import io.camunda.operate.webapp.rest.dto.SequenceFlowDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@Component
public class DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);
  public static final String PROCESS_BPMN_PROCESS_ID = "basicProcess";
  public static final int PROCESS_INSTANCE_COUNT = 51;
  public static final int INCIDENT_COUNT = 32;
  public static final int COUNT_OF_CANCEL_OPERATION = 9;
  public static final int COUNT_OF_RESOLVE_OPERATION = 8;
  private static final DateTimeFormatter ARCHIVER_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

  //data change
  public static final String NEW_BPMN_PROCESS_ID = "testProcess2";
  public static final int CANCELLED_PROCESS_INSTANCES = 3;
  public static final int NEW_PROCESS_INSTANCES_COUNT = 13;

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  private Random random = new Random();

  private List<Long> processInstanceKeys = new ArrayList<>();

  private StatefulRestTemplate operateRestClient;

  private RestHighLevelClient esClient;

  @Autowired
  private OperateAPICaller operateAPICaller;

  private void init(BackupRestoreTestContext testContext) {
    this.zeebeClient = ZeebeClient.newClientBuilder().gatewayAddress(testContext.getExternalZeebeContactPoint()).usePlaintext().build();
    this.esClient = testContext.getEsClient();
    this.operateRestClient = testContext.getOperateRestClient();
  }

  public void createData(BackupRestoreTestContext testContext) {
    init(testContext);
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

    deployProcess(PROCESS_BPMN_PROCESS_ID);
    processInstanceKeys = startProcessInstances(PROCESS_BPMN_PROCESS_ID, PROCESS_INSTANCE_COUNT);
    completeTasks("task1", PROCESS_INSTANCE_COUNT);
    createIncidents("task2", INCIDENT_COUNT);

    waitUntilAllDataAreImported();

    for (int i = 0; i < COUNT_OF_CANCEL_OPERATION; i++) {
      createOperation(OperationType.CANCEL_PROCESS_INSTANCE, processInstanceKeys.size() * 10);
    }
    logger.info("{} operations of type {} started", COUNT_OF_CANCEL_OPERATION, OperationType.CANCEL_PROCESS_INSTANCE);

    for (int i = 0; i < COUNT_OF_RESOLVE_OPERATION; i++) {
      createOperation(OperationType.RESOLVE_INCIDENT, processInstanceKeys.size() * 10);
    }
    logger.info("{} operations of type {} started", COUNT_OF_RESOLVE_OPERATION, OperationType.RESOLVE_INCIDENT);

    waitTillSomeInstancesAreArchived();

    try {
      esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error in refreshing indices", e);
    }
    logger.info("Data generation completed in: {} s",
        ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
    testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
  }

  @PreDestroy
  public void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
    if (esClient != null) {
      try {
        esClient.close();
      } catch (IOException e) {
        throw new OperateRuntimeException(e);
      }
    }
  }

  private void waitTillSomeInstancesAreArchived() {
    waitUntilAllDataAreImported();

    int count = 0, maxWait = 30;
    logger.info("Waiting for archived data (max: {} sec)", maxWait * 10);
    while (!someInstancesAreArchived() && count < maxWait) {
      ThreadUtil.sleepFor(10 * 1000L);
      count++;
    }
    if (count == maxWait) {
      logger.error("There must be some archived instances");
      throw new RuntimeException("Data generation was not full: no archived instances");
    }
  }

  private void waitUntilAllDataAreImported() {
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    long loadedProcessInstances = 0;
    int count = 0, maxWait = 101;
    while (PROCESS_INSTANCE_COUNT > loadedProcessInstances && count < maxWait) {
      count++;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private boolean someInstancesAreArchived() {
    try {
      SearchResponse search = esClient.search(
          new SearchRequest("operate-*_" + ARCHIVER_DATE_TIME_FORMATTER.format(Instant.now())), RequestOptions.DEFAULT);
      return search.getHits().getTotalHits().value > 0;
    } catch (IOException e) {
      throw new RuntimeException("Exception occurred while checking archived indices: " + e.getMessage(), e);
    }
  }

  private void createOperation(OperationType operationType, int maxAttempts) {
    logger.debug("Try to create Operation {} ( {} attempts)", operationType.name(), maxAttempts);
    boolean operationStarted = false;
    int attempts = 0;
    while (!operationStarted && attempts < maxAttempts) {
      Long processInstanceKey = chooseKey(processInstanceKeys);
      operationStarted = operateAPICaller.createOperation(processInstanceKey, operationType);
      attempts++;
    }
    if (operationStarted) {
      logger.debug("Operation {} started", operationType.name());
    } else {
      throw new RuntimeException(String.format("Operation %s could not started", operationType.name()));
    }
  }

  private void createIncidents(String jobType, int numberOfIncidents) {
    ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", numberOfIncidents);
    logger.info("{} incidents in {} created", numberOfIncidents, jobType);
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, "worker", "{\"varOut\": \"value2\"}", count);
    logger.info("{} tasks {} completed", count, jobType);
  }

  private List<Long> startProcessInstances(String bpmnProcessId, int numberOfProcessInstances) {
    List<Long> processInstanceKeys = new ArrayList<>();
    for (int i = 0; i < numberOfProcessInstances; i++) {
      long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, "{\"var1\": \"value1\"}");
      logger.debug("Started processInstance {} for process {}", processInstanceKey, bpmnProcessId);
      processInstanceKeys.add(processInstanceKey);
    }
    logger.info("{} processInstances started", processInstanceKeys.size());
    return processInstanceKeys;
  }

  private void deployProcess(String bpmnProcessId) {
    String processDefinitionKey = ZeebeTestUtil.deployProcess(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
    logger.info("Deployed process {} with key {}", bpmnProcessId, processDefinitionKey);
  }

  private BpmnModelInstance createModel(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId).startEvent("start").serviceTask("task1").zeebeJobType("task1")
        .zeebeInput("=var1", "varIn").zeebeOutput("=varOut", "var2").serviceTask("task2").zeebeJobType("task2").serviceTask("task3").zeebeJobType("task3").serviceTask("task4").zeebeJobType("task4").serviceTask("task5")
        .zeebeJobType("task5").endEvent().done();
  }

  private Long chooseKey(List<Long> keys) {
    return keys.get(random.nextInt(keys.size()));
  }

  private long countEntitiesFor(SearchRequest searchRequest) {
    try {
      searchRequest.source().size(1000);
      SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return searchResponse.getHits().getTotalHits().value;
    } catch (IOException e) {
      throw new OperateRuntimeException(e);
    }
  }

  private String getAliasFor(String index) {
    return String.format("operate-%s-*_alias", index);
  }

  public void assertData() {
    try {
      RetryOperation
          .newBuilder()
          .noOfRetry(10)
          .delayInterval(2000, TimeUnit.MILLISECONDS)
          .retryPredicate(result -> !(boolean) result)
          .retryConsumer(() -> {
            try {
              assertDataOneAttempt();
              return true;
            } catch (AssertionError er) {
              return false;
            }
      }).build().retry();


    } catch (Exception ex) {
      throw new OperateRuntimeException(ex);
    }
  }

  private void assertDataOneAttempt() {
    ProcessGroupDto[] groupedProcesses = operateAPICaller.getGroupedProcesses();
    assertThat(groupedProcesses).hasSize(1);
    assertThat(groupedProcesses[0].getBpmnProcessId()).isEqualTo(PROCESS_BPMN_PROCESS_ID);

    ListViewResponseDto processInstances = operateAPICaller.getProcessInstances();
    assertThat(processInstances.getProcessInstances().size()).isEqualTo(PROCESS_INSTANCE_COUNT);

    //check sequence flows from random process instances
    int count = 0;
    while (count <= 10) {
      SequenceFlowDto[] sequenceFlows = operateAPICaller.getSequenceFlows(
          processInstances.getProcessInstances().get(random.nextInt(PROCESS_INSTANCE_COUNT)).getId());
      assertThat(sequenceFlows.length).isEqualTo(PROCESS_INSTANCE_COUNT * 2);
      count++;
    }

    //count incident process instances
    ListViewResponseDto incidentProcessInstances = operateAPICaller.getIncidentProcessInstances();
    assertThat(incidentProcessInstances.getProcessInstances().size()).isEqualTo(PROCESS_INSTANCE_COUNT);
    assertThat(incidentProcessInstances.getTotalCount()).isBetween(
        Long.valueOf(INCIDENT_COUNT - (COUNT_OF_CANCEL_OPERATION + COUNT_OF_RESOLVE_OPERATION)),
        Long.valueOf(INCIDENT_COUNT)
    );
  }

  public void changeData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting changing the data...");

    deployProcess(NEW_BPMN_PROCESS_ID);
    startProcessInstances(NEW_BPMN_PROCESS_ID, NEW_PROCESS_INSTANCES_COUNT);

    ListViewResponseDto incidentProcessInstances = operateAPICaller.getIncidentProcessInstances();
    //resolve several incidents
    for (int i = 0; i < 10; i++) {
      operateAPICaller.createOperation(Long.valueOf(incidentProcessInstances.getProcessInstances()
              .get(random.nextInt((int) incidentProcessInstances.getTotalCount())).getId()),
          OperationType.RESOLVE_INCIDENT);
    }

    //cancel several instances
    for (int i = 0; i < CANCELLED_PROCESS_INSTANCES; i++) {
      operateAPICaller.createOperation(Long.valueOf(incidentProcessInstances.getProcessInstances()
              .get(random.nextInt((int) incidentProcessInstances.getTotalCount())).getId()),
          OperationType.CANCEL_PROCESS_INSTANCE);
    }

    logger.info("Data changing completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
  }

  public void assertDataAfterChange() throws Exception {
    RetryOperation.newBuilder().noOfRetry(10).delayInterval(2000, TimeUnit.MILLISECONDS)
        .retryPredicate(result -> !(boolean) result).retryConsumer(() -> {
          try {
            ProcessGroupDto[] groupedProcesses = operateAPICaller.getGroupedProcesses();
            assertThat(groupedProcesses).hasSize(2);
            assertThat(groupedProcesses).extracting(ProcessGroupDto::getBpmnProcessId)
                .containsExactlyInAnyOrder(PROCESS_BPMN_PROCESS_ID, NEW_BPMN_PROCESS_ID);
            ListViewResponseDto processInstances = operateAPICaller.getProcessInstances();
            assertThat(processInstances.getProcessInstances().size()).isEqualTo(
                PROCESS_INSTANCE_COUNT + NEW_PROCESS_INSTANCES_COUNT - CANCELLED_PROCESS_INSTANCES);
            return true;
          } catch (AssertionError er) {
            return false;
          }
        })
        .build().retry();
  }

}

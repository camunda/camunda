/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.entities.TaskState;
import io.camunda.tasklist.qa.util.ZeebeTestUtil;
import io.camunda.tasklist.schema.templates.DraftTaskVariableTemplate;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.SaveVariablesRequest;
import io.camunda.tasklist.webapp.graphql.entity.TaskDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.RandomStringUtils;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class DataGenerator {

  public static final String PROCESS_BPMN_PROCESS_ID = "basicProcess";
  public static final String PROCESS_BPMN_PROCESS_ID_2 = "basicProcess2";
  public static final int PROCESS_INSTANCE_COUNT = 49;
  private static final int ALL_DRAFT_TASK_VARIABLES_COUNT = PROCESS_INSTANCE_COUNT * 2;
  private static final int COMPLETED_TASKS_COUNT = 11;
  private static final int DRAFT_TASK_VARIABLES_COUNT_AFTER_TASKS_COMPLETION =
      ALL_DRAFT_TASK_VARIABLES_COUNT - PROCESS_INSTANCE_COUNT * 2;
  private static final Logger LOGGER = LoggerFactory.getLogger(DataGenerator.class);
  //  private static final DateTimeFormatter ARCHIVER_DATE_TIME_FORMATTER =
  // DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different
   * versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  private RestHighLevelClient esClient;

  @Autowired private TasklistAPICaller tasklistAPICaller;

  private Random random = new Random();

  private List<Long> processInstanceKeys = new ArrayList<>();

  private void init(BackupRestoreTestContext testContext) {
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(testContext.getExternalZeebeContactPoint())
            .usePlaintext()
            .build();
    //    operateRestClient = new StatefulRestTemplate(testContext.getExternalOperateHost(),
    // testContext.getExternalOperatePort(), testContext.getExternalOperateContextPath());
    //    operateRestClient.loginWhenNeeded();
    this.esClient = testContext.getEsClient();
    //    this.operateRestClient = testContext.getOperateRestClient();
  }

  public void createData(BackupRestoreTestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      LOGGER.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcess(createModel1(PROCESS_BPMN_PROCESS_ID), PROCESS_BPMN_PROCESS_ID);
      processInstanceKeys = startProcessInstances(PROCESS_BPMN_PROCESS_ID, PROCESS_INSTANCE_COUNT);

      waitUntilAllDataAreImported();

      claimAllTasks();

      addDraftVariablesForAllTasks();

      try {
        esClient.indices().refresh(new RefreshRequest("tasklist-*"), RequestOptions.DEFAULT);
      } catch (IOException e) {
        LOGGER.error("Error in refreshing indices", e);
      }
      LOGGER.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
    } finally {
      closeClients();
    }
  }

  private void addDraftVariablesForAllTasks() throws IOException {
    final var tasks = tasklistAPICaller.getAllTasks().getList("$.data.tasks", TaskDTO.class);
    LOGGER.info("Found '{}' tasks, adding 2 draft variables to each task.", tasks.size());
    tasks.stream()
        .parallel()
        .forEach(
            task ->
                tasklistAPICaller.saveDraftTaskVariables(
                    task.getId(),
                    new SaveVariablesRequest()
                        .setVariables(
                            List.of(
                                new VariableInputDTO()
                                    .setName("var1")
                                    .setValue("\"updatedDraftVarValue\""),
                                new VariableInputDTO()
                                    .setName("draftVar")
                                    .setValue(
                                        "\"" + RandomStringUtils.randomAlphanumeric(10) + "\"")))));
  }

  private void claimAllTasks() {
    final UpdateByQueryRequest updateRequest =
        new UpdateByQueryRequest(getMainIndexNameFor(TaskTemplate.INDEX_NAME))
            .setQuery(QueryBuilders.matchAllQuery())
            .setScript(
                new Script(
                    ScriptType.INLINE,
                    "painless",
                    "ctx._source.assignee = 'demo'",
                    Collections.emptyMap()))
            .setRefresh(true);
    try {
      esClient.updateByQuery(updateRequest, RequestOptions.DEFAULT);
    } catch (ElasticsearchException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }

  private void waitUntilAllDataAreImported() throws IOException {
    LOGGER.info("Wait till data is imported.");
    final SearchRequest searchRequest = new SearchRequest(getAliasFor(TaskTemplate.INDEX_NAME));
    long loadedProcessInstances = 0;
    int count = 0;
    final int maxWait = 101;
    while (PROCESS_INSTANCE_COUNT > loadedProcessInstances && count < maxWait) {
      count++;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private List<Long> startProcessInstances(String bpmnProcessId, int numberOfProcessInstances) {
    for (int i = 0; i < numberOfProcessInstances; i++) {
      final long processInstanceKey =
          ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, "{\"var1\": \"value1\"}");
      LOGGER.debug("Started processInstance {} for process {}", processInstanceKey, bpmnProcessId);
      processInstanceKeys.add(processInstanceKey);
    }
    LOGGER.info("{} processInstances started", processInstanceKeys.size());
    return processInstanceKeys;
  }

  private void deployProcess(BpmnModelInstance bpmnModelInstance, String bpmnProcessId) {
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, bpmnModelInstance, bpmnProcessId + ".bpmn");
    LOGGER.info("Deployed process {} with key {}", bpmnProcessId, processDefinitionKey);
  }

  private BpmnModelInstance createModel1(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .userTask("task1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
        .serviceTask("task5")
        .zeebeJobType("task5")
        .endEvent()
        .done();
  }

  private BpmnModelInstance createModel2(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .userTask("task2")
        .endEvent()
        .done();
  }

  private long countEntitiesFor(SearchRequest searchRequest) throws IOException {
    searchRequest.source().size(1000);
    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits().value;
  }

  private String getAliasFor(String index) {
    return String.format("tasklist-%s-*_alias", index);
  }

  private String getMainIndexNameFor(String index) {
    return String.format("tasklist-%s-*_", index);
  }

  @Retryable(value = AssertionError.class, maxAttempts = 10, backoff = @Backoff(delay = 2000))
  public void assertData() throws IOException {
    try {
      final GraphQLResponse response = tasklistAPICaller.getAllTasks();
      assertTrue(response.isOk());
      assertEquals(String.valueOf(PROCESS_INSTANCE_COUNT), response.get("$.data.tasks.length()"));
      assertEquals("task1", response.get("$.data.tasks[0].name"));
      assertEquals("CREATED", response.get("$.data.tasks[0].taskState"));

      final var draftVariablesSearchRequest =
          new SearchRequest(getAliasFor(DraftTaskVariableTemplate.INDEX_NAME));
      assertThat(countEntitiesFor(draftVariablesSearchRequest))
          .isEqualTo(ALL_DRAFT_TASK_VARIABLES_COUNT);
    } catch (AssertionError er) {
      LOGGER.warn("Error when asserting data: " + er.getMessage());
      throw er;
    }
  }

  @Retryable(value = AssertionError.class, maxAttempts = 10, backoff = @Backoff(delay = 2000))
  public void assertDataAfterChange() throws IOException {
    try {
      List<TaskDTO> tasks = tasklistAPICaller.getTasks("task1");
      assertThat(tasks).hasSize(PROCESS_INSTANCE_COUNT);
      assertThat(tasks)
          .filteredOn(t -> t.getTaskState().equals(TaskState.COMPLETED))
          .hasSize(COMPLETED_TASKS_COUNT);
      assertThat(tasks)
          .filteredOn(t -> t.getTaskState().equals(TaskState.CREATED))
          .hasSize(PROCESS_INSTANCE_COUNT - COMPLETED_TASKS_COUNT);

      tasks = tasklistAPICaller.getTasks("task2");
      assertThat(tasks).hasSize(PROCESS_INSTANCE_COUNT);
      assertThat(tasks).extracting("taskState").containsOnly(TaskState.CREATED);

      final var draftVariablesSearchRequest =
          new SearchRequest(getAliasFor(DraftTaskVariableTemplate.INDEX_NAME));
      // after task completion all draft variables associated with a task will be deleted
      assertThat(countEntitiesFor(draftVariablesSearchRequest))
          .isEqualTo(DRAFT_TASK_VARIABLES_COUNT_AFTER_TASKS_COMPLETION);
    } catch (AssertionError er) {
      LOGGER.warn("Error when asserting data: " + er.getMessage());
      throw er;
    }
  }

  public void changeData(BackupRestoreTestContext testContext) throws IOException {
    init(testContext);
    try {
      // complete tasks (they are already claimed)
      completeTasks("task1", COMPLETED_TASKS_COUNT);

      // start other process instance
      deployProcess(createModel2(PROCESS_BPMN_PROCESS_ID_2), PROCESS_BPMN_PROCESS_ID_2);

      processInstanceKeys =
          startProcessInstances(PROCESS_BPMN_PROCESS_ID_2, PROCESS_INSTANCE_COUNT);
    } finally {
      closeClients();
    }
  }

  private void completeTasks(String taskBpmnId, int completedTasksCount) throws IOException {
    final List<TaskDTO> tasks = tasklistAPICaller.getTasks("task1");
    for (int i = 0; i < COMPLETED_TASKS_COUNT; i++) {
      tasklistAPICaller.completeTask(tasks.get(i).getId(), "{name: \"varOut\", value: \"123\"}");
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.it;

import static io.zeebe.tasklist.graphql.TaskIT.GET_TASK_QUERY_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.tasklist.archiver.Archiver;
import io.zeebe.tasklist.archiver.TaskArchiverJob;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.es.schema.templates.TaskTemplate;
import io.zeebe.tasklist.es.schema.templates.TaskVariableTemplate;
import io.zeebe.tasklist.exceptions.ArchiverException;
import io.zeebe.tasklist.util.CollectionUtil;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.util.TasklistZeebeIntegrationTest;
import io.zeebe.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class ArchiverIT extends TasklistZeebeIntegrationTest {

  @Autowired private BeanFactory beanFactory;

  @Autowired private Archiver archiver;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TaskMutationResolver taskMutationResolver;

  private TaskArchiverJob archiverJob;

  private Random random = new Random();

  private DateTimeFormatter dateTimeFormatter;

  @Before
  public void before() {
    super.before();
    dateTimeFormatter =
        DateTimeFormatter.ofPattern(tasklistProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    archiverJob = beanFactory.getBean(TaskArchiverJob.class, partitionHolder.getPartitionIds());
    try {
      FieldSetter.setField(
          taskMutationResolver,
          TaskMutationResolver.class.getDeclaredField("zeebeClient"),
          super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
    clearMetrics();
  }

  @Test
  public void testArchivingTasks() throws ArchiverException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    // having
    // deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and finish instances 2 days ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.DAYS);
    final List<String> ids1 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count1, endDate1);

    // start and finish instances 1 day ago
    final int count2 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(1, ChronoUnit.DAYS);
    final List<String> ids2 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count2, endDate2);

    // start instances 1 day ago
    final int count3 = random.nextInt(6) + 3;
    final List<String> ids3 =
        startInstances(processId, flowNodeBpmnId, count3, currentTime.minus(1, ChronoUnit.DAYS));

    brokerRule.getClock().setCurrentTime(currentTime);

    // when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count2);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    assertThat(archiverJob.archiveNextBatch())
        .isEqualTo(
            0); // 3rd run should not move anything, as the rest of the tasks are not completed

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(count1, ids1, endDate1);
    assertTasksInCorrectIndex(count2, ids2, endDate2);
    assertTasksInCorrectIndex(count3, ids3, null);

    assertAllInstancesInAlias(count1 + count2 + count3, ids1.get(0));
  }

  private void assertAllInstancesInAlias(int count, String id) throws IOException {
    assertThat(tester.getAllTasks().get("$.data.tasks.length()")).isEqualTo(String.valueOf(count));
    final String taskId =
        tester.getByQuery(String.format(GET_TASK_QUERY_PATTERN, id)).get("$.data.task.id");
    assertThat(taskId).isEqualTo(id);
  }

  @Test
  public void testArchivingOnlyOneHourOldData() throws ArchiverException, IOException {
    brokerRule.getClock().pinCurrentTime();
    final Instant currentTime = brokerRule.getClock().getCurrentTime();

    // having
    // deploy process
    brokerRule.getClock().setCurrentTime(currentTime.minus(4, ChronoUnit.DAYS));
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "task1";
    deployProcessWithOneFlowNode(processId, flowNodeBpmnId);

    // start and finish instances 2 hours ago
    final int count1 = random.nextInt(6) + 3;
    final Instant endDate1 = currentTime.minus(2, ChronoUnit.HOURS);
    final List<String> ids1 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count1, endDate1);

    // start and finish instances 50 minutes ago
    final int count2 = random.nextInt(6) + 3;
    final Instant endDate2 = currentTime.minus(50, ChronoUnit.MINUTES);
    final List<String> ids2 =
        startInstancesAndCompleteTasks(processId, flowNodeBpmnId, count2, endDate2);

    brokerRule.getClock().setCurrentTime(currentTime);

    // when
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(count1);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    // 2rd run should not move anything, as the rest of the tasks are completed less then 1 hour ago
    assertThat(archiverJob.archiveNextBatch()).isEqualTo(0);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    // then
    assertTasksInCorrectIndex(count1, ids1, endDate1);
    assertTasksInCorrectIndex(count2, ids2, null);
  }

  private void deployProcessWithOneFlowNode(String processId, String flowNodeBpmnId) {
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(flowNodeBpmnId)
            .zeebeJobType(tasklistProperties.getImporter().getJobType())
            .endEvent()
            .done();
    tester.deployWorkflow(workflow, processId + ".bpmn").waitUntil().workflowIsDeployed();
  }

  private void assertTasksInCorrectIndex(int tasksCount, List<String> ids, Instant endDate)
      throws IOException {
    assertTaskIndex(tasksCount, ids, endDate);
    assertDependentIndex(
        taskVariableTemplate.getMainIndexName(), TaskVariableTemplate.TASK_ID, ids, endDate);
  }

  private void assertTaskIndex(int tasksCount, List<String> ids, Instant endDate)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiver.getDestinationIndexName(
              taskTemplate.getMainIndexName(), dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(taskTemplate.getMainIndexName(), "");
    }
    final IdsQueryBuilder idsQ = idsQuery().addIds(CollectionUtil.toSafeArrayOfStrings(ids));

    final SearchRequest searchRequest =
        new SearchRequest(destinationIndexName)
            .source(new SearchSourceBuilder().query(constantScoreQuery(idsQ)).size(100));

    final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);

    final List<TaskEntity> taskEntities =
        ElasticsearchUtil.mapSearchHits(
            response.getHits().getHits(), objectMapper, TaskEntity.class);
    assertThat(taskEntities).hasSize(tasksCount);
    assertThat(taskEntities).extracting(TaskTemplate.ID).containsExactlyInAnyOrderElementsOf(ids);
    if (endDate != null) {
      assertThat(taskEntities)
          .extracting(TaskTemplate.COMPLETION_TIME)
          .allMatch(ed -> ((OffsetDateTime) ed).toInstant().equals(endDate));
    }
  }

  private void assertDependentIndex(
      String mainIndexName, String idFieldName, List<String> ids, Instant endDate)
      throws IOException {
    final String destinationIndexName;
    if (endDate != null) {
      destinationIndexName =
          archiver.getDestinationIndexName(mainIndexName, dateTimeFormatter.format(endDate));
    } else {
      destinationIndexName = archiver.getDestinationIndexName(mainIndexName, "");
    }
    final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest request =
        new SearchRequest(destinationIndexName)
            .source(new SearchSourceBuilder().query(q).size(100));
    final List<String> idsFromEls =
        ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
    assertThat(idsFromEls).as(mainIndexName).isSubsetOf(ids);
  }

  private List<String> startInstancesAndCompleteTasks(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    brokerRule.getClock().setCurrentTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startWorkflowInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .claimAndCompleteHumanTask(flowNodeBpmnId)
              .waitUntil()
              .taskIsCompleted(flowNodeBpmnId)
              .getTaskId());
    }
    return ids;
  }

  private List<String> startInstances(
      String processId, String flowNodeBpmnId, int count, Instant currentTime) {
    assertThat(count).isGreaterThan(0);
    brokerRule.getClock().setCurrentTime(currentTime);
    final List<String> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(
          tester
              .startWorkflowInstance(processId, "{\"var\": 123}")
              .waitUntil()
              .taskIsCreated(flowNodeBpmnId)
              .getTaskId());
    }
    return ids;
  }
}

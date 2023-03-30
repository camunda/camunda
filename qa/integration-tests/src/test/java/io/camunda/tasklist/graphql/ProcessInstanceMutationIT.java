/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.graphql;

import static io.camunda.tasklist.util.CollectionUtil.map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceDependant;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.graphql.mutation.TaskMutationResolver;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceMutationIT extends TasklistZeebeIntegrationTest {

  private static final List<Class<?>> SHOULD_PROCESS_INSTANCE_DEPENDANTS =
      List.of(FlowNodeInstanceIndex.class, VariableIndex.class, TaskTemplate.class);

  @Autowired private TaskMutationResolver taskMutationResolver;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired private TaskVariableTemplate taskVariableIndex;

  @Autowired private RestHighLevelClient esClient;

  @Before
  public void before() {
    super.before();
  }

  @Test
  public void notExistingProcessInstanceCantBeDeleted() {
    // Given nothing
    // when
    final Boolean deleted = tester.deleteProcessInstance("235");
    // then
    assertThat(deleted).isFalse();
  }

  @Test
  public void completedProcessInstanceCanBeDeleted() {
    // given
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String processInstanceId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .claimAndCompleteHumanTask(
                flowNodeBpmnId, "delete", "\"me\"", "when", "\"processInstance is completed\"")
            .then()
            .waitUntil()
            .processInstanceIsCompleted()
            .getProcessInstanceId();
    // when
    final Boolean deleted = tester.deleteProcessInstance(processInstanceId);
    // then
    assertThat(deleted).isTrue();

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    assertWhoIsAProcessInstanceDependant();
    assertThatProcessDependantsAreDeleted(processInstanceId);
    assertThatVariablesForTasksOfProcessInstancesAreDeleted();
  }

  @Test
  public void notCompletedProcessInstanceCantBeDeleted() {
    // given
    final String bpmnProcessId = "testProcess";
    final String flowNodeBpmnId = "taskA";
    final String processInstanceId =
        tester
            .createAndDeploySimpleProcess(bpmnProcessId, flowNodeBpmnId)
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId)
            .getProcessInstanceId();
    // when
    final Boolean deleted = tester.deleteProcessInstance(processInstanceId);
    // then
    assertThat(deleted).isFalse();
  }

  protected void assertThatProcessDependantsAreDeleted(String processInstanceId) {
    final QueryBuilder processInstanceIdQuery =
        termQuery(ProcessInstanceDependant.PROCESS_INSTANCE_ID, processInstanceId);
    assertThat(
            processInstanceDependants.stream()
                .allMatch(
                    processInstanceDependant ->
                        countByQuery(
                                processInstanceDependant.getAllIndicesPattern(),
                                processInstanceIdQuery)
                            == 0))
        .isTrue();
  }

  protected void assertThatVariablesForTasksOfProcessInstancesAreDeleted() {
    final QueryBuilder variablesOfTaskQuery = matchAllQuery();
    assertThat(countByQuery(taskVariableIndex.getFullQualifiedName(), variablesOfTaskQuery))
        .isZero();
  }

  protected void assertWhoIsAProcessInstanceDependant() {
    final List<Class<?>> currentDependants = map(processInstanceDependants, Object::getClass);
    assertThat(currentDependants).hasSameElementsAs(SHOULD_PROCESS_INSTANCE_DEPENDANTS);
  }

  protected long countByQuery(String indexName, QueryBuilder query) {
    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.fetchSource(false);
    try {
      final SearchResponse searchResponse =
          esClient.search(
              new SearchRequest(indexName).source(searchSourceBuilder), RequestOptions.DEFAULT);
      return searchResponse.getHits().getTotalHits().value;
    } catch (IOException e) {
      return -1;
    }
  }
}

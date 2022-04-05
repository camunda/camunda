/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.writer.ProcessInstanceWriter;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceWriterIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ProcessInstanceWriter processInstanceWriter;

  @Autowired
  private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Before
  public void before() {
    super.before();
    tester.deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed();
  }

  @Test
  public void shouldDeleteFinishedInstanceById() throws IOException {
    // given
    final Long finishedProcessInstanceKey =
        tester.startProcessInstance("process", null)
            .and().completeTask("task", null)
            .waitUntil()
            .processInstanceIsFinished()
            .getProcessInstanceKey();
    // when
    processInstanceWriter.deleteInstanceById(finishedProcessInstanceKey);
    // and indices are updated
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    // then
    assertThatProcessInstanceIsDeleted(finishedProcessInstanceKey);
    // and
    assertThatDependantsAreAlsoDeleted(finishedProcessInstanceKey);
  }

  @Test
  public void shouldDeleteCanceledInstanceById() throws Exception {
    // given
    final Long canceledProcessInstanceKey =
        tester.startProcessInstance("process", null)
            .and().completeTask("task", null)
            .and().cancelProcessInstanceOperation()
            .waitUntil().operationIsCompleted()
            .getProcessInstanceKey();
    // when
    processInstanceWriter.deleteInstanceById(canceledProcessInstanceKey);
    // and indices are updated
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    // then
    assertThatProcessInstanceIsDeleted(canceledProcessInstanceKey);
    // and
    assertThatDependantsAreAlsoDeleted(canceledProcessInstanceKey);
  }

  @Test(expected = NotFoundException.class)
  public void shouldFailDeleteWithNotExistingId() throws IOException {
    // given nothing
    // when
    processInstanceWriter.deleteInstanceById(42L);
    // then throw exception
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldFailDeleteInstanceByIdWithInvalidState() throws IOException {
    // given
    final Long runningProcessInstance = tester.deployProcess("single-task.bpmn")
        .waitUntil().processIsDeployed()
        .startProcessInstance("process", null)
        .and()
        .waitUntil().processInstanceIsStarted()
        .getProcessInstanceKey();
    // when
    processInstanceWriter.deleteInstanceById(runningProcessInstance);
    // then throw exception
  }

  private void assertThatProcessInstanceIsDeleted(final Long canceledProcessInstanceKey) {
    try {
      processInstanceReader.getProcessInstanceByKey(canceledProcessInstanceKey);
      Assert.fail("Process instance wasn't deleted");
    } catch (NotFoundException nfe) {
      // should be thrown
    }
  }

  private void assertThatDependantsAreAlsoDeleted(final long finishedProcessInstanceKey) {
    processInstanceDependants.stream()
        .filter(t -> !(t instanceof OperationTemplate))
        .map(dependant -> buildSearchRequest(finishedProcessInstanceKey, dependant))
        .map(this::esSearch)
        .forEach(response -> assertThat(response.getHits().getTotalHits().value).isZero());
  }


  private SearchRequest buildSearchRequest(Long finishedProcessInstanceKey,
      ProcessInstanceDependant dependant) {
    return new SearchRequest(dependant.getFullQualifiedName() + "*").source(
        new SearchSourceBuilder().query(
            QueryBuilders.termQuery(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, finishedProcessInstanceKey)));
  }

  private SearchResponse esSearch(SearchRequest searchRequest) {
    try {
      return esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      throw new RuntimeException("Test failed with exception");
    }
  }

}

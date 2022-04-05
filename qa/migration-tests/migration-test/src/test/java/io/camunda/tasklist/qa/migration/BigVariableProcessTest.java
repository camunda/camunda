/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.qa.migration;

import static io.camunda.tasklist.qa.migration.v100.BigVariableProcessDataGenerator.BIG_VAR_NAME;
import static io.camunda.tasklist.qa.migration.v100.BigVariableProcessDataGenerator.SMALL_VAR_NAME;
import static io.camunda.tasklist.qa.migration.v100.BigVariableProcessDataGenerator.SMALL_VAR_VALUE;
import static io.camunda.tasklist.schema.indices.VariableIndex.PROCESS_INSTANCE_ID;
import static io.camunda.tasklist.schema.templates.TaskTemplate.BPMN_PROCESS_ID;
import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.property.ImportProperties;
import io.camunda.tasklist.qa.migration.util.AbstractMigrationTest;
import io.camunda.tasklist.qa.migration.v100.BigVariableProcessDataGenerator;
import io.camunda.tasklist.qa.util.VariablesUtil;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;

public class BigVariableProcessTest extends AbstractMigrationTest {

  private String bpmnProcessId = BigVariableProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> taskIds;
  private List<String> processInstanceIds;
  private String processId;

  @Before
  public void before() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    sleepFor(5_000);
    findTaskIds();
    findProcessInstanceIds();
  }

  public void findTaskIds() {
    if (taskIds == null) {
      final SearchRequest searchRequest = new SearchRequest(taskTemplate.getAlias());
      // task list
      searchRequest.source().query(termQuery(BPMN_PROCESS_ID, bpmnProcessId));
      try {
        taskIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(taskIds).hasSize(1);
    }
  }

  public void findProcessInstanceIds() {
    if (processInstanceIds == null) {
      final SearchRequest searchRequest = new SearchRequest(taskTemplate.getAlias());
      // Process instances list
      searchRequest.source().query(termQuery(BPMN_PROCESS_ID, bpmnProcessId));
      try {
        processInstanceIds =
            ElasticsearchUtil.scrollFieldToList(searchRequest, PROCESS_INSTANCE_ID, esClient);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Test
  public void testTasks() throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(termsQuery(PROCESS_INSTANCE_ID, processInstanceIds)));
    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    assertThat(searchResponse.getHits().getTotalHits().value).isEqualTo(1);
  }

  @Test
  public void testVariables() {
    final List<VariableEntity> vars =
        entityReader.searchEntitiesFor(
            new SearchRequest(variableIndex.getAlias())
                .source(
                    new SearchSourceBuilder()
                        .query(termsQuery(PROCESS_INSTANCE_ID, processInstanceIds))),
            VariableEntity.class);
    assertThat(vars.size()).isEqualTo(2);
    boolean found = false;
    for (VariableEntity var : vars) {
      if (var.getName().equals(BIG_VAR_NAME)) {
        found = true;
        assertThat(var.getValue().length())
            .isEqualTo(ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD);
        assertThat(var.getFullValue().length())
            .isGreaterThan(ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD);
        assertThat(var.getFullValue()).endsWith(VariablesUtil.VAR_SUFFIX + "\"");
        assertThat(var.getIsPreview()).isTrue();
      } else if (var.getName().equals(SMALL_VAR_NAME)) {
        found = true;
        assertThat(var.getValue().length())
            .isLessThan(ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD);
        assertThat(var.getFullValue().length())
            .isLessThan(ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD);
        assertThat(var.getFullValue()).isEqualTo(SMALL_VAR_VALUE);
        assertThat(var.getValue()).isEqualTo(SMALL_VAR_VALUE);
        assertThat(var.getIsPreview()).isFalse();
      }
      assertThat(found).isTrue();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration;

import static io.camunda.tasklist.schema.templates.TaskTemplate.BPMN_PROCESS_ID;
import static io.camunda.tasklist.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.qa.migration.util.AbstractMigrationTest;
import io.camunda.tasklist.qa.migration.util.BasicProcessDataGenerator;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BasicProcessTest extends AbstractMigrationTest {

  private final String bpmnProcessId = BasicProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> taskIds;

  @BeforeEach
  public void findTaskIds() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    if (taskIds == null) {
      sleepFor(5_000);
      final SearchRequest searchRequest = new SearchRequest(taskTemplate.getAlias());
      // task list
      searchRequest.source().query(termQuery(BPMN_PROCESS_ID, bpmnProcessId));
      try {
        taskIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(taskIds).hasSize(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT);
    }
  }

  @Test
  public void testImportPositions() {
    final List<ImportPositionEntity> importPositions =
        entityReader.getEntitiesFor(importPositionIndex.getAlias(), ImportPositionEntity.class);
    assertThat(importPositions.isEmpty())
        .describedAs("There should exists at least 1 ImportPosition")
        .isFalse();
  }

  @Test
  public void testTasks() throws IOException {
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(new SearchSourceBuilder().query(termQuery(BPMN_PROCESS_ID, bpmnProcessId)));
    final List<TaskEntity> tasks = entityReader.searchEntitiesFor(searchRequest, TaskEntity.class);
    assertThat(tasks).hasSize(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT);
    // 1.3.0-0_task_clean_assignee.json is not anymore part in the migration path
    // assertThat(tasks).extracting(ASSIGNEE).containsOnlyNulls();
  }

  @Test
  public void testUsers() {
    final List<UserEntity> users =
        entityReader.getEntitiesFor(userIndex.getAlias(), UserEntity.class);
    assertThat(users.size()).isEqualTo(3);
    assertThat(users).extracting(UserIndex.USER_ID).contains("demo", "act", "view");
  }
}

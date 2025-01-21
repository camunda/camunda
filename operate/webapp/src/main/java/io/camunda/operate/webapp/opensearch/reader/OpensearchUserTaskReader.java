/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.and;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.tasklist.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.SnapshotTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchUserTaskReader extends OpensearchAbstractReader implements UserTaskReader {

  private static final Query TASK_QUERY =
      term(TaskTemplate.JOIN_FIELD_NAME, TaskJoinRelationshipType.TASK.getType());
  private final TaskTemplate taskTemplate;
  private final SnapshotTaskVariableTemplate snapshotTaskVariableTemplate;

  public OpensearchUserTaskReader(
      final TaskTemplate taskTemplate,
      @Qualifier("operateSnapshotTaskVariableTemplate")
          final SnapshotTaskVariableTemplate snapshotTaskVariableTemplate) {
    this.taskTemplate = taskTemplate;
    this.snapshotTaskVariableTemplate = snapshotTaskVariableTemplate;
  }

  @Override
  public List<TaskEntity> getUserTasks() {
    final var request =
        searchRequestBuilder(taskTemplate.getAlias()).query(withTenantCheck(TASK_QUERY));
    return richOpenSearchClient.doc().searchValues(request, TaskEntity.class);
  }

  @Override
  public Optional<TaskEntity> getUserTaskByFlowNodeInstanceKey(final long flowNodeInstanceKey) {
    final var request =
        searchRequestBuilder(taskTemplate.getAlias())
            .query(
                withTenantCheck(
                    and(
                        TASK_QUERY,
                        term(TaskTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceKey))));

    final var hits = richOpenSearchClient.doc().search(request, TaskEntity.class).hits();
    if (hits.total().value() == 1) {
      return Optional.of(hits.hits().get(0).source());
    }
    return Optional.empty();
  }

  @Override
  public List<SnapshotTaskVariableEntity> getUserTaskVariables(final long taskKey) {
    final var userTaskKeyQuery =
        QueryBuilders.term()
            .field(SnapshotTaskVariableTemplate.TASK_ID)
            .value(FieldValue.of(String.valueOf(taskKey)))
            .build()
            .toQuery();

    final var request =
        searchRequestBuilder(snapshotTaskVariableTemplate.getAlias())
            .query(withTenantCheck(userTaskKeyQuery));
    return richOpenSearchClient.doc().scrollValues(request, SnapshotTaskVariableEntity.class);
  }
}

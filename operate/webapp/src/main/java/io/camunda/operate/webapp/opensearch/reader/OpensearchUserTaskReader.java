/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.exists;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import io.camunda.webapps.schema.entities.tasklist.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.tasklist.TaskVariableEntity;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch._types.query_dsl.HasParentQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchUserTaskReader extends OpensearchAbstractReader implements UserTaskReader {

  private final TaskTemplate userTaskTemplate;

  public OpensearchUserTaskReader(final TaskTemplate userTaskTemplate) {
    this.userTaskTemplate = userTaskTemplate;
  }

  @Override
  public List<TaskEntity> getUserTasks() {
    final var request =
        searchRequestBuilder(userTaskTemplate.getAlias()).query(withTenantCheck(matchAll()));
    return richOpenSearchClient.doc().searchValues(request, TaskEntity.class);
  }

  @Override
  public Optional<TaskEntity> getUserTaskByFlowNodeInstanceKey(final long flowNodeInstanceKey) {
    final var request =
        searchRequestBuilder(userTaskTemplate.getAlias())
            .query(withTenantCheck(term(TaskTemplate.FLOW_NODE_INSTANCE_ID, flowNodeInstanceKey)));
    final var hits = richOpenSearchClient.doc().search(request, TaskEntity.class).hits();
    if (hits.total().value() == 1) {
      return Optional.of(hits.hits().get(0).source());
    }
    return Optional.empty();
  }

  @Override
  public List<TaskVariableEntity> getUserTaskCompletedVariables(final long flowNodeInstanceKey) {
    final Query hasParentQuery =
        new HasParentQuery.Builder()
            .parentType(TaskJoinRelationshipType.TASK.getType())
            .query(term(TaskTemplate.ID, flowNodeInstanceKey))
            .build()
            .query();

    // Make sure `name` field exists, indicating only variables are present in the result set
    final Query existsQuery = exists(TaskTemplate.VARIABLE_NAME);

    final Query combinedQuery =
        QueryBuilders.bool().must(hasParentQuery, existsQuery).build().toQuery();

    final var request =
        searchRequestBuilder(userTaskTemplate.getAlias()).query(withTenantCheck(combinedQuery));
    return richOpenSearchClient.doc().searchValues(request, TaskVariableEntity.class);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.term;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.webapp.reader.UserTaskReader;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.tasklist.TaskEntity;
import java.util.List;
import java.util.Optional;
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
}

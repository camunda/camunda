/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.OpenSearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.listview.VariableListViewEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.v86.templates.TasklistListViewTemplate;
import io.camunda.tasklist.store.ListViewStore;
import io.camunda.tasklist.util.OpenSearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ListViewStoreOpenSearch implements ListViewStore {

  private static final Logger LOGGER = Logger.getLogger(ListViewStoreOpenSearch.class.getName());

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired private TasklistListViewTemplate tasklistListViewTemplate;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public void removeVariableByFlowNodeInstanceId(final String flowNodeInstanceId) {
    try {
      final SearchRequest.Builder searchRequest =
          OpenSearchUtil.createSearchRequest(tasklistListViewTemplate)
              .query(
                  q ->
                      q.term(
                          t ->
                              t.field(TasklistListViewTemplate.VARIABLE_SCOPE_KEY)
                                  .value(FieldValue.of(flowNodeInstanceId))));

      OpenSearchUtil.scrollWith(
          searchRequest,
          osClient,
          hits -> {
            final List<BulkOperation> bulkOperations = new ArrayList<>();
            for (final Hit<VariableListViewEntity> hit : hits) {
              final VariableListViewEntity variableListViewEntity = hit.source();
              final BulkOperation bulkOperation =
                  new BulkOperation.Builder()
                      .delete(
                          d ->
                              d.index(tasklistListViewTemplate.getFullQualifiedName())
                                  .id(variableListViewEntity.getId())
                                  .routing(flowNodeInstanceId))
                      .build();
              bulkOperations.add(bulkOperation);
            }

            if (!bulkOperations.isEmpty()) {
              final BulkRequest.Builder bulkRequest =
                  new BulkRequest.Builder().operations(bulkOperations);
              try {
                OpenSearchUtil.processBulkRequest(osClient, bulkRequest.build());
              } catch (final PersistenceException e) {
                throw new TasklistRuntimeException(
                    String.format(
                        "Error removing set of variables for flowNodeInstanceId [%s]",
                        flowNodeInstanceId),
                    e);
              }
            }
          },
          null, // No need for an aggregation processor
          VariableListViewEntity.class,
          null // No need for processing first response metadata
          );

    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error deleting task variables for flowNodeInstanceId [%s]", flowNodeInstanceId),
          e);
    }
  }

  @Override
  public List<VariableListViewEntity> getVariablesByVariableName(final String varName) {
    final List<VariableListViewEntity> variableList = new ArrayList<>();

    try {
      final SearchRequest.Builder searchRequest =
          new SearchRequest.Builder()
              .index(tasklistListViewTemplate.getAlias())
              .query(
                  q ->
                      q.term(
                          t ->
                              t.field(TasklistListViewTemplate.VARIABLE_NAME)
                                  .value(FieldValue.of(varName))));

      OpenSearchUtil.scrollWith(
          searchRequest,
          osClient,
          hits ->
              hits.stream()
                  .map(Hit::source)
                  .filter(Objects::nonNull)
                  .map(v -> objectMapper.convertValue(v, VariableListViewEntity.class))
                  .forEach(variableList::add),
          null, // No need for an aggregation processor
          VariableListViewEntity.class,
          null // No need for processing first response metadata
          );

    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format("Error retrieving variables for variable name [%s]", varName), e);
    }

    return variableList;
  }

  @Override
  public void persistTaskVariables(final Collection<VariableListViewEntity> finalVariables) {
    if (finalVariables.isEmpty()) {
      return;
    }
    try {
      final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

      for (final VariableListViewEntity variableEntity : finalVariables) {
        bulkRequest.operations(op -> op.update(createUpsertRequest(variableEntity)));
      }

      osClient.bulk(bulkRequest.build());

    } catch (final IOException e) {
      throw new TasklistRuntimeException("Error processing bulk request for task variables", e);
    }
  }

  private UpdateOperation<Object> createUpsertRequest(
      final VariableListViewEntity variableListViewEntity) {
    final Map<String, Object> updateFields = new HashMap<>();
    updateFields.put(TasklistListViewTemplate.VARIABLE_NAME, variableListViewEntity.getName());
    updateFields.put(TasklistListViewTemplate.VARIABLE_VALUE, variableListViewEntity.getValue());
    updateFields.put(
        TasklistListViewTemplate.VARIABLE_FULL_VALUE, variableListViewEntity.getFullValue());
    updateFields.put(TasklistListViewTemplate.JOIN_FIELD_NAME, variableListViewEntity.getJoin());

    return new UpdateOperation.Builder<>()
        .index(tasklistListViewTemplate.getFullQualifiedName()) // Index name
        .routing(variableListViewEntity.getScopeKey())
        .id(variableListViewEntity.getId())
        .document(CommonUtils.getJsonObjectFromEntity(updateFields))
        .upsert(CommonUtils.getJsonObjectFromEntity(variableListViewEntity))
        .retryOnConflict(UPDATE_RETRY_COUNT)
        .build();
  }
}

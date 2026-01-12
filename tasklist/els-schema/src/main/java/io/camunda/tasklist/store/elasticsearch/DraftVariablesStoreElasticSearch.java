/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.entities.usertask.DraftTaskVariableEntity;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(ElasticSearchCondition.class)
public class DraftVariablesStoreElasticSearch implements DraftVariableStore {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DraftVariablesStoreElasticSearch.class);

  @Autowired private ElasticsearchTenantHelper tenantHelper;

  @Autowired
  @Qualifier("tasklistEs8Client")
  private ElasticsearchClient es8Client;

  @Autowired private DraftTaskVariableTemplate draftTaskVariableTemplate;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public void createOrUpdate(final Collection<DraftTaskVariableEntity> draftVariables) {
    try {
      final var bulkOperations = draftVariables.stream().map(this::createUpsertRequest).toList();

      final var bulkRequest =
          BulkRequest.of(b -> b.operations(bulkOperations).refresh(Refresh.WaitFor));

      final var bulkResponse = es8Client.bulk(bulkRequest);

      if (bulkResponse.errors()) {
        final var errorMessages =
            bulkResponse.items().stream()
                .filter(item -> item.error() != null)
                .map(item -> item.error().reason())
                .collect(java.util.stream.Collectors.joining(", "));
        throw new TasklistRuntimeException(
            "Failed to upsert draft variables. Errors: " + errorMessages);
      }
    } catch (final IOException e) {
      throw new TasklistRuntimeException("Error upserting draft variables", e);
    }
  }

  @Override
  public long deleteAllByTaskId(final String taskId) {
    final var query = ElasticsearchUtil.termsQuery(DraftTaskVariableTemplate.TASK_ID, taskId);
    final var request =
        DeleteByQueryRequest.of(
            b -> b.index(draftTaskVariableTemplate.getFullQualifiedName()).query(query));

    try {
      final var response = es8Client.deleteByQuery(request);
      return response.deleted(); // Return the count of deleted documents
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to delete draft task variable instances for task [%s]",
              taskId),
          e);
    }
  }

  @Override
  public List<DraftTaskVariableEntity> getVariablesByTaskIdAndVariableNames(
      final String taskId, final List<String> variableNames) {
    try {
      final var taskIdQuery =
          ElasticsearchUtil.termsQuery(DraftTaskVariableTemplate.TASK_ID, taskId);

      final var query =
          CollectionUtils.isEmpty(variableNames)
              ? taskIdQuery
              : ElasticsearchUtil.joinWithAnd(
                  taskIdQuery,
                  ElasticsearchUtil.termsQuery(DraftTaskVariableTemplate.NAME, variableNames));

      final var searchRequestBuilder =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(draftTaskVariableTemplate.getFullQualifiedName())
              .query(query);

      final var scrollStream =
          ElasticsearchUtil.scrollAllStream(
              es8Client, searchRequestBuilder, DraftTaskVariableEntity.class);

      return scrollStream
          .flatMap(response -> response.hits().hits().stream())
          .map(Hit::source)
          .filter(Objects::nonNull)
          .toList();
    } catch (final Exception e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error executing the query to get draft task variable instances for task [%s] with variable names %s",
              taskId, variableNames),
          e);
    }
  }

  @Override
  public Optional<DraftTaskVariableEntity> getById(final String variableId) {
    try {
      final var query = ElasticsearchUtil.termsQuery(DraftTaskVariableTemplate.ID, variableId);
      final var tenantAwareQuery = tenantHelper.makeQueryTenantAware(query);

      final var searchRequest =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(draftTaskVariableTemplate.getFullQualifiedName())
              .query(tenantAwareQuery)
              .build();

      final var response = es8Client.search(searchRequest, DraftTaskVariableEntity.class);

      if (response.hits().total().value() == 0) {
        return Optional.empty();
      }

      return Optional.ofNullable(response.hits().hits().get(0).source());
    } catch (final IOException e) {
      LOGGER.error(
          String.format("Error retrieving draft task variable instance with ID [%s]", variableId),
          e);
      return Optional.empty();
    }
  }

  @Override
  public List<String> getDraftVariablesIdsByTaskIds(final List<String> taskIds) {
    try {
      final var query = ElasticsearchUtil.termsQuery(DraftTaskVariableTemplate.TASK_ID, taskIds);

      final var searchRequestBuilder =
          new co.elastic.clients.elasticsearch.core.SearchRequest.Builder()
              .index(draftTaskVariableTemplate.getFullQualifiedName())
              .query(query)
              .source(s -> s.filter(f -> f.includes(DraftTaskVariableTemplate.ID)));

      return ElasticsearchUtil.scrollAllStream(
              es8Client, searchRequestBuilder, ElasticsearchUtil.MAP_CLASS)
          .flatMap(response -> response.hits().hits().stream())
          .map(Hit::id)
          .filter(Objects::nonNull)
          .toList();
    } catch (final Exception e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }

  private BulkOperation createUpsertRequest(final DraftTaskVariableEntity draftVariableEntity) {
    final var updateFields = new HashMap<String, Object>();
    updateFields.put(DraftTaskVariableTemplate.TASK_ID, draftVariableEntity.getTaskId());
    updateFields.put(DraftTaskVariableTemplate.NAME, draftVariableEntity.getName());
    updateFields.put(DraftTaskVariableTemplate.VALUE, draftVariableEntity.getValue());
    updateFields.put(DraftTaskVariableTemplate.FULL_VALUE, draftVariableEntity.getFullValue());
    updateFields.put(DraftTaskVariableTemplate.IS_PREVIEW, draftVariableEntity.getIsPreview());

    return BulkOperation.of(
        op ->
            op.update(
                u ->
                    u.index(draftTaskVariableTemplate.getFullQualifiedName())
                        .id(draftVariableEntity.getId())
                        .retryOnConflict(UPDATE_RETRY_COUNT)
                        .action(a -> a.doc(updateFields).upsert(draftVariableEntity))));
  }
}

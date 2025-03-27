/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.DraftVariableStore;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.tasklist.util.OpenSearchUtil;
import io.camunda.webapps.schema.descriptors.tasklist.template.DraftTaskVariableTemplate;
import io.camunda.webapps.schema.entities.usertask.DraftTaskVariableEntity;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryRequest;
import org.opensearch.client.opensearch.core.DeleteByQueryResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Conditional(OpenSearchCondition.class)
public class DraftVariablesStoreOpenSearch implements DraftVariableStore {

  private static final Logger LOGGER = LoggerFactory.getLogger(DraftVariablesStoreOpenSearch.class);

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired private TenantAwareOpenSearchClient tenantAwareClient;
  @Autowired private DraftTaskVariableTemplate draftTaskVariableTemplate;

  public void createOrUpdate(Collection<DraftTaskVariableEntity> draftVariables) {
    final BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
    final List<BulkOperation> operations =
        draftVariables.stream().map(this::createUpsertRequest).toList();

    bulkRequest.operations(operations);
    bulkRequest.refresh(Refresh.WaitFor);
    try {
      OpenSearchUtil.processBulkRequest(osClient, bulkRequest.build());
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private BulkOperation createUpsertRequest(DraftTaskVariableEntity draftVariableEntity) {
    return new BulkOperation.Builder()
        .update(
            UpdateOperation.of(
                u ->
                    u.index(draftTaskVariableTemplate.getFullQualifiedName())
                        .id(draftVariableEntity.getId())
                        .docAsUpsert(true)
                        .document(CommonUtils.getJsonObjectFromEntity(draftVariableEntity))
                        .retryOnConflict(UPDATE_RETRY_COUNT)))
        .build();
  }

  public long deleteAllByTaskId(String taskId) {
    final DeleteByQueryRequest.Builder request = new DeleteByQueryRequest.Builder();
    request
        .index(draftTaskVariableTemplate.getFullQualifiedName())
        .query(
            q ->
                q.term(
                    term ->
                        term.field(DraftTaskVariableTemplate.TASK_ID)
                            .value(FieldValue.of(taskId))));

    try {
      final DeleteByQueryResponse response = osClient.deleteByQuery(request.build());
      return response.deleted(); // Return the count of deleted documents
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to delete draft task variable instances for task [%s]",
              taskId),
          e);
    }
  }

  public List<DraftTaskVariableEntity> getVariablesByTaskIdAndVariableNames(
      String taskId, List<String> variableNames) {
    try {

      final BoolQuery.Builder queryBuilder = new BoolQuery.Builder();
      queryBuilder.must(
          q ->
              q.term(
                  term ->
                      term.field(DraftTaskVariableTemplate.TASK_ID).value(FieldValue.of(taskId))));

      // Add variable names to query only if the list is not empty
      if (!CollectionUtils.isEmpty(variableNames)) {
        queryBuilder.must(
            q ->
                q.terms(
                    terms ->
                        terms
                            .field(DraftTaskVariableTemplate.NAME)
                            .terms(
                                v ->
                                    v.value(
                                        variableNames.stream()
                                            .map(m -> FieldValue.of(m))
                                            .collect(Collectors.toList())))));
      }

      // final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder);

      final SearchRequest.Builder searchRequestBuilder =
          new SearchRequest.Builder()
              .index(draftTaskVariableTemplate.getFullQualifiedName())
              .query(q -> q.bool(queryBuilder.build()));

      return OpenSearchUtil.scroll(searchRequestBuilder, DraftTaskVariableEntity.class, osClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error executing the query to get draft task variable instances for task [%s] with variable names %s",
              taskId, variableNames),
          e);
    }
  }

  public Optional<DraftTaskVariableEntity> getById(String variableId) {
    try {
      final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
      searchRequest.index(draftTaskVariableTemplate.getFullQualifiedName());
      searchRequest.query(
          q ->
              q.term(
                  term ->
                      term.field(DraftTaskVariableTemplate.ID).value(FieldValue.of(variableId))));

      final SearchResponse<DraftTaskVariableEntity> searchResponse =
          tenantAwareClient.search(searchRequest, DraftTaskVariableEntity.class);

      final List<Hit<DraftTaskVariableEntity>> hits = searchResponse.hits().hits();
      if (hits.size() == 0) {
        return Optional.empty();
      }

      final Hit<DraftTaskVariableEntity> hit = hits.get(0);
      return Optional.of(hit.source());
    } catch (IOException e) {
      LOGGER.error(
          String.format("Error retrieving draft task variable instance with ID [%s]", variableId),
          e);
      return Optional.empty();
    }
  }

  @Override
  public List<String> getDraftVariablesIdsByTaskIds(List<String> taskIds) {
    final SearchRequest.Builder searchRequest = new SearchRequest.Builder();
    searchRequest
        .index(draftTaskVariableTemplate.getFullQualifiedName())
        .query(
            q ->
                q.terms(
                    terms ->
                        terms
                            .field(DraftTaskVariableTemplate.TASK_ID)
                            .terms(
                                t ->
                                    t.value(
                                        taskIds.stream()
                                            .map(FieldValue::of)
                                            .collect(Collectors.toList())))))
        .fields(f -> f.field(DraftTaskVariableTemplate.ID));

    try {
      return OpenSearchUtil.scrollIdsToList(searchRequest, osClient);
    } catch (IOException e) {
      throw new TasklistRuntimeException(e.getMessage(), e);
    }
  }
}

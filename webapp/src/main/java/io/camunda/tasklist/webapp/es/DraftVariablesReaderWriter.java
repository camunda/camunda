/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.DraftTaskVariableEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.templates.DraftTaskVariableTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DraftVariablesReaderWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(DraftVariablesReaderWriter.class);

  @Autowired private RestHighLevelClient esClient;
  @Autowired private DraftTaskVariableTemplate draftTaskVariableTemplate;
  @Autowired private ObjectMapper objectMapper;

  public void createOrUpdate(Collection<DraftTaskVariableEntity> draftVariables) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (DraftTaskVariableEntity variableEntity : draftVariables) {
      bulkRequest.add(createUpsertRequest(variableEntity));
    }
    try {
      ElasticsearchUtil.processBulkRequest(
          esClient, bulkRequest, WriteRequest.RefreshPolicy.WAIT_UNTIL);
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private UpdateRequest createUpsertRequest(DraftTaskVariableEntity draftVariableEntity) {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(DraftTaskVariableTemplate.TASK_ID, draftVariableEntity.getTaskId());
      updateFields.put(DraftTaskVariableTemplate.NAME, draftVariableEntity.getName());
      updateFields.put(DraftTaskVariableTemplate.VALUE, draftVariableEntity.getValue());
      updateFields.put(DraftTaskVariableTemplate.FULL_VALUE, draftVariableEntity.getFullValue());
      updateFields.put(DraftTaskVariableTemplate.IS_PREVIEW, draftVariableEntity.getIsPreview());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(draftTaskVariableTemplate.getFullQualifiedName())
          .id(draftVariableEntity.getId())
          .upsert(objectMapper.writeValueAsString(draftVariableEntity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to upsert task variable instance [%s]",
              draftVariableEntity.getId()),
          e);
    }
  }

  public long deleteAllByTaskId(String taskId) {
    final DeleteByQueryRequest request =
        new DeleteByQueryRequest(draftTaskVariableTemplate.getFullQualifiedName());
    request.setQuery(QueryBuilders.termQuery(DraftTaskVariableTemplate.TASK_ID, taskId));

    try {
      final BulkByScrollResponse response = esClient.deleteByQuery(request, RequestOptions.DEFAULT);
      return response.getDeleted(); // Return the count of deleted documents
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
      final BoolQueryBuilder queryBuilder =
          QueryBuilders.boolQuery()
              .must(QueryBuilders.termQuery(DraftTaskVariableTemplate.TASK_ID, taskId));

      // Add variable names to query only if the list is not empty
      if (CollectionUtils.isNotEmpty(variableNames)) {
        queryBuilder.must(QueryBuilders.termsQuery(DraftTaskVariableTemplate.NAME, variableNames));
      }

      final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(queryBuilder);

      final SearchRequest searchRequest =
          new SearchRequest(draftTaskVariableTemplate.getFullQualifiedName());
      searchRequest.source(sourceBuilder);

      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      final SearchHits hits = searchResponse.getHits();
      final List<DraftTaskVariableEntity> results = new ArrayList<>();

      for (SearchHit hit : hits) {
        final String sourceAsString = hit.getSourceAsString();
        final DraftTaskVariableEntity entity =
            objectMapper.readValue(sourceAsString, DraftTaskVariableEntity.class);
        results.add(entity);
      }

      return results;
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
      final SearchRequest searchRequest =
          new SearchRequest(draftTaskVariableTemplate.getFullQualifiedName());
      final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
      sourceBuilder.query(QueryBuilders.termQuery(DraftTaskVariableTemplate.ID, variableId));
      searchRequest.source(sourceBuilder);

      final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);

      final SearchHits hits = searchResponse.getHits();
      if (hits.getTotalHits().value == 0) {
        return Optional.empty();
      }

      final SearchHit hit = hits.getAt(0);
      final String sourceAsString = hit.getSourceAsString();
      final DraftTaskVariableEntity entity =
          objectMapper.readValue(sourceAsString, DraftTaskVariableEntity.class);
      return Optional.of(entity);
    } catch (IOException e) {
      LOGGER.error(
          String.format("Error retrieving draft task variable instance with ID [%s]", variableId),
          e);
      return Optional.empty();
    }
  }
}

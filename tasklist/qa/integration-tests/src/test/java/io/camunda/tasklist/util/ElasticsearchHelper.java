/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.ElasticsearchUtil.*;
import static org.elasticsearch.index.query.QueryBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.descriptors.template.SnapshotTaskVariableTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ElasticsearchHelper implements NoSqlHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchHelper.class);

  private static final Integer QUERY_SIZE = 100;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired
  @Qualifier("tasklistSnapshotTaskVariableTemplate")
  private SnapshotTaskVariableTemplate taskVariableTemplate;

  @Autowired
  @Qualifier("tasklistVariableTemplate")
  private VariableTemplate variableIndex;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public TaskEntity getTask(final String taskId) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(taskTemplate.getAlias())
              .source(new SearchSourceBuilder().query(termQuery(TaskTemplate.KEY, taskId)));
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getHits().length == 1) {
        return fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundApiException(
            String.format("Could not find  task for taskId [%s].", taskId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the task: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<TaskEntity> getTask(final String processInstanceId, final String flowNodeBpmnId) {
    TermQueryBuilder piId = null;
    if (processInstanceId != null) {
      piId = termQuery(TaskTemplate.PROCESS_INSTANCE_ID, processInstanceId);
    }
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            piId, termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, flowNodeBpmnId)))
                    .sort(TaskTemplate.CREATION_TIME, SortOrder.DESC));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value >= 1) {
        return mapSearchHits(response.getHits().getHits(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundApiException(
            String.format(
                "Could not find task for processInstanceId [%s] with flowNodeBpmnId [%s].",
                processInstanceId, flowNodeBpmnId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public boolean checkVariableExists(final String taskId, final String varName) {
    final TermQueryBuilder taskIdQ = termQuery(SnapshotTaskVariableTemplate.TASK_ID, taskId);
    final TermQueryBuilder varNameQ = termQuery(SnapshotTaskVariableTemplate.NAME, varName);
    final SearchRequest searchRequest =
        new SearchRequest(taskVariableTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(joinWithAnd(taskIdQ, varNameQ))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value > 0;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public boolean checkVariablesExist(final String[] varNames) {
    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getFullQualifiedName())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(termsQuery(VariableTemplate.NAME, varNames))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value == varNames.length;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<String> getIdsFromIndex(
      final String idFieldName, final String index, final List<String> ids) {
    final TermsQueryBuilder q = termsQuery(idFieldName, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest request =
        new SearchRequest(index).source(new SearchSourceBuilder().query(q).size(QUERY_SIZE));

    try {
      final List<String> idsFromEls =
          ElasticsearchUtil.scrollFieldToList(request, idFieldName, esClient);
      return idsFromEls;
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public List<TaskEntity> getTasksFromIdAndIndex(final String index, final List<String> ids) {
    final TermsQueryBuilder q =
        termsQuery(TaskTemplate.KEY, CollectionUtil.toSafeArrayOfStrings(ids));
    final SearchRequest searchRequest =
        new SearchRequest(index).source(new SearchSourceBuilder().query(q).size(QUERY_SIZE));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return mapSearchHits(response.getHits().getHits(), objectMapper, TaskEntity.class);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<TaskEntity> getAllTasks(final String index) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(index)
              .source(
                  new SearchSourceBuilder().query(constantScoreQuery(matchAllQuery())).size(100));
      final SearchResponse response;
      response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return ElasticsearchUtil.mapSearchHits(
          response.getHits().getHits(), objectMapper, TaskEntity.class);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Long countIndexResult(final String index) {
    try {
      final QueryBuilder query = matchAllQuery();
      final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
      searchSourceBuilder.query(query);
      searchSourceBuilder.fetchSource(false);
      final SearchResponse searchResponse =
          esClient.search(
              new SearchRequest(index).source(searchSourceBuilder), RequestOptions.DEFAULT);
      return searchResponse.getHits().getTotalHits().value;
    } catch (final IOException e) {
      return -1L;
    }
  }

  @Override
  public Boolean isIndexDynamicMapping(final IndexDescriptor indexDescriptor, final String dynamic)
      throws IOException {
    final Map<String, MappingMetadata> mappings =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexDescriptor.getFullQualifiedName()), RequestOptions.DEFAULT)
            .getMappings();
    final MappingMetadata mappingMetadata = mappings.get(indexDescriptor.getFullQualifiedName());
    return mappingMetadata.getSourceAsMap().get("dynamic").equals(dynamic);
  }

  @Override
  public Map<String, Object> getFieldDescription(final IndexDescriptor indexDescriptor)
      throws IOException {
    final Map<String, MappingMetadata> mappings =
        esClient
            .indices()
            .get(
                new GetIndexRequest(indexDescriptor.getFullQualifiedName()), RequestOptions.DEFAULT)
            .getMappings();
    final Map<String, Object> source =
        mappings.get(indexDescriptor.getFullQualifiedName()).getSourceAsMap();
    return (Map<String, Object>) source.get("properties");
  }

  @Override
  public Boolean indexHasAlias(final String index, final String alias) throws IOException {
    final GetIndexResponse getIndexResponse =
        esClient.indices().get(new GetIndexRequest(index), RequestOptions.DEFAULT);
    return getIndexResponse.getAliases().size() == 1
        && getIndexResponse.getAliases().get(index).get(0).alias().equals(alias);
  }

  @Override
  public void delete(final String index, final String id) throws IOException {
    final DeleteRequest request = new DeleteRequest().index(index).id(id);
    esClient.delete(request, RequestOptions.DEFAULT);
  }

  @Override
  public void update(final String index, final String id, final Map<String, Object> jsonMap)
      throws IOException {
    final UpdateRequest request = new UpdateRequest().index(index).id(id).doc(jsonMap);
    esClient.update(request, RequestOptions.DEFAULT);
  }
}

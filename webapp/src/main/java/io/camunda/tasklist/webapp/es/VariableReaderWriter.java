/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.es;

import static io.camunda.tasklist.schema.indices.VariableIndex.ID;
import static io.camunda.tasklist.schema.indices.VariableIndex.NAME;
import static io.camunda.tasklist.schema.indices.VariableIndex.SCOPE_FLOW_NODE_ID;
import static io.camunda.tasklist.util.CollectionUtil.isNotEmpty;
import static io.camunda.tasklist.util.ElasticsearchUtil.UPDATE_RETRY_COUNT;
import static io.camunda.tasklist.util.ElasticsearchUtil.createSearchRequest;
import static io.camunda.tasklist.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.tasklist.util.ElasticsearchUtil.scroll;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.templates.TaskVariableTemplate;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.service.VariableService.GetVariablesRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class VariableReaderWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(VariableReaderWriter.class);

  @Autowired private RestHighLevelClient esClient;
  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Autowired private VariableIndex variableIndex;
  @Autowired private TaskVariableTemplate taskVariableTemplate;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private ObjectMapper objectMapper;

  public List<VariableEntity> getVariablesByFlowNodeInstanceIds(
      List<String> flowNodeInstanceIds, List<String> varNames, final Set<String> fieldNames) {
    final TermsQueryBuilder flowNodeInstanceKeyQ =
        termsQuery(SCOPE_FLOW_NODE_ID, flowNodeInstanceIds);
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(constantScoreQuery(joinWithAnd(flowNodeInstanceKeyQ, varNamesQ)));
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);

    final SearchRequest searchRequest =
        new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public Map<String, List<VariableDTO>> getTaskVariablesPerTaskId(
      final List<GetVariablesRequest> requests) {

    if (requests == null || requests.size() == 0) {
      return new HashMap<>();
    }
    final TermsQueryBuilder taskIdsQ =
        termsQuery(
            TaskVariableTemplate.TASK_ID,
            requests.stream().map(GetVariablesRequest::getTaskId).collect(toList()));
    final List<String> varNames =
        requests.stream()
            .map(GetVariablesRequest::getVarNames)
            .flatMap(x -> x == null ? null : x.stream())
            .collect(toList());
    TermsQueryBuilder varNamesQ = null;
    if (isNotEmpty(varNames)) {
      varNamesQ = termsQuery(VariableIndex.NAME, varNames);
    }

    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(constantScoreQuery(joinWithAnd(taskIdsQ, varNamesQ)));
    applyFetchSourceForTaskVariableTemplate(
        searchSourceBuilder,
        requests
            .get(0)
            .getFieldNames()); // we assume here that all requests has the same list of fields

    final SearchRequest searchRequest =
        new SearchRequest(taskVariableTemplate.getAlias()).source(searchSourceBuilder);
    try {
      final List<TaskVariableEntity> entities =
          scroll(searchRequest, TaskVariableEntity.class, objectMapper, esClient);
      return entities.stream()
          .collect(
              groupingBy(
                  TaskVariableEntity::getTaskId,
                  mapping(tv -> VariableDTO.createFrom(tv), toList())));
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public void persistTaskVariables(final Collection<TaskVariableEntity> finalVariables) {
    final BulkRequest bulkRequest = new BulkRequest();
    for (TaskVariableEntity variableEntity : finalVariables) {
      bulkRequest.add(createUpsertRequest(variableEntity));
    }
    try {
      ElasticsearchUtil.processBulkRequest(esClient, bulkRequest, RefreshPolicy.WAIT_UNTIL);
    } catch (PersistenceException ex) {
      throw new TasklistRuntimeException(ex);
    }
  }

  private UpdateRequest createUpsertRequest(TaskVariableEntity variableEntity) {
    try {
      final Map<String, Object> updateFields = new HashMap<>();
      updateFields.put(TaskVariableTemplate.TASK_ID, variableEntity.getTaskId());
      updateFields.put(TaskVariableTemplate.NAME, variableEntity.getName());
      updateFields.put(TaskVariableTemplate.VALUE, variableEntity.getValue());

      // format date fields properly
      final Map<String, Object> jsonMap =
          objectMapper.readValue(objectMapper.writeValueAsString(updateFields), HashMap.class);

      return new UpdateRequest()
          .index(taskVariableTemplate.getFullQualifiedName())
          .id(variableEntity.getId())
          .upsert(objectMapper.writeValueAsString(variableEntity), XContentType.JSON)
          .doc(jsonMap)
          .retryOnConflict(UPDATE_RETRY_COUNT);

    } catch (IOException e) {
      throw new TasklistRuntimeException(
          String.format(
              "Error preparing the query to upsert task variable instance [%s]",
              variableEntity.getId()),
          e);
    }
  }

  public List<FlowNodeInstanceEntity> getFlowNodeInstances(final List<String> processInstanceIds) {
    final TermsQueryBuilder processInstanceKeyQuery =
        termsQuery(FlowNodeInstanceIndex.PROCESS_INSTANCE_ID, processInstanceIds);
    final SearchRequest searchRequest =
        new SearchRequest(flowNodeInstanceIndex.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(processInstanceKeyQuery))
                    .sort(FlowNodeInstanceIndex.POSITION, SortOrder.ASC)
                    .size(tasklistProperties.getElasticsearch().getBatchSize()));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all flow nodes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public VariableEntity getRuntimeVariable(final String variableId, Set<String> fieldNames) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(idsQuery().addIds(variableId));
    applyFetchSourceForVariableIndex(searchSourceBuilder, fieldNames);
    final SearchRequest request =
        new SearchRequest(variableIndex.getAlias()).source(searchSourceBuilder);
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(),
            objectMapper,
            VariableEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Unique variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(String.format("Variable with id %s was not found", variableId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  public TaskVariableEntity getTaskVariable(final String variableId, Set<String> fieldNames) {
    final SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder().query(idsQuery().addIds(variableId));
    applyFetchSourceForTaskVariableTemplate(searchSourceBuilder, fieldNames);
    final SearchRequest request =
        createSearchRequest(taskVariableTemplate).source(searchSourceBuilder);
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value == 1) {
        return fromSearchHit(
            response.getHits().getHits()[0].getSourceAsString(),
            objectMapper,
            TaskVariableEntity.class);
      } else if (response.getHits().getTotalHits().value > 1) {
        throw new NotFoundException(
            String.format("Unique task variable with id %s was not found", variableId));
      } else {
        throw new NotFoundException(
            String.format("Task variable with id %s was not found", variableId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining task variable: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  private void applyFetchSourceForVariableIndex(
      SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames = VariableIndex.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(ID);
      elsFieldNames.add(NAME);
      elsFieldNames.add(SCOPE_FLOW_NODE_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fetchSource(includesFields, null);
    }
  }

  private void applyFetchSourceForTaskVariableTemplate(
      SearchSourceBuilder searchSourceBuilder, final Set<String> fieldNames) {
    final String[] includesFields;
    if (isNotEmpty(fieldNames)) {
      final Set<String> elsFieldNames =
          TaskVariableTemplate.getElsFieldsByGraphqlFields(fieldNames);
      elsFieldNames.add(TaskVariableTemplate.ID);
      elsFieldNames.add(TaskVariableTemplate.NAME);
      elsFieldNames.add(TaskVariableTemplate.TASK_ID);
      includesFields = elsFieldNames.toArray(new String[elsFieldNames.size()]);
      searchSourceBuilder.fetchSource(includesFields, null);
    }
  }
}

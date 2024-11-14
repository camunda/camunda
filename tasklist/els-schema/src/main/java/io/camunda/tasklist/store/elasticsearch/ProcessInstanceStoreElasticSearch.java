/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static io.camunda.tasklist.util.ElasticsearchUtil.QueryType.ALL;
import static io.camunda.tasklist.v86.schema.indices.TasklistProcessInstanceDependant.PROCESS_INSTANCE_ID;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.enums.DeletionStatus;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.ProcessInstanceStore;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.tasklist.util.ElasticsearchUtil;
import io.camunda.tasklist.v86.entities.ProcessInstanceEntity;
import io.camunda.tasklist.v86.schema.indices.TasklistProcessInstanceDependant;
import io.camunda.tasklist.v86.schema.indices.TasklistProcessInstanceIndex;
import io.camunda.tasklist.v86.schema.templates.TasklistTaskVariableTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ProcessInstanceStoreElasticSearch implements ProcessInstanceStore {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceStoreElasticSearch.class);

  @Autowired TasklistProcessInstanceIndex processInstanceIndex;

  @Autowired TaskStore taskStore;

  @Autowired List<TasklistProcessInstanceDependant> processInstanceDependants;

  @Autowired TasklistTaskVariableTemplate taskVariableTemplate;

  @Autowired RetryElasticsearchClient retryElasticsearchClient;

  @Autowired TenantAwareElasticsearchClient tenantAwareClient;

  @Autowired TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Override
  public DeletionStatus deleteProcessInstance(final String processInstanceId) {
    if (tasklistProperties.getMultiTenancy().isEnabled() && getById(processInstanceId).isEmpty()) {
      return DeletionStatus.NOT_FOUND;
    }

    // Don't need to validate for canceled/completed process instances
    // because only completed will be imported by ProcessInstanceZeebeRecordProcessor
    final boolean processInstanceWasDeleted =
        retryElasticsearchClient.deleteDocument(
            processInstanceIndex.getFullQualifiedName(), processInstanceId);
    // if deleted -> process instance was really finished and we can delete dependent data
    if (processInstanceWasDeleted) {
      return deleteProcessInstanceDependantsFor(processInstanceId);
    } else {
      return DeletionStatus.NOT_FOUND;
    }
  }

  private DeletionStatus deleteProcessInstanceDependantsFor(final String processInstanceId) {
    final List<String> dependantTaskIds = getDependantTasksIdsFor(processInstanceId);
    boolean deleted = false;
    for (final TasklistProcessInstanceDependant dependant : processInstanceDependants) {
      deleted =
          retryElasticsearchClient.deleteDocumentsByQuery(
                  dependant.getAllIndicesPattern(),
                  termQuery(PROCESS_INSTANCE_ID, processInstanceId))
              || deleted;
    }
    if (deleted) {
      deleteVariablesFor(dependantTaskIds);
      return DeletionStatus.DELETED;
    }
    return DeletionStatus.FAILED;
  }

  private List<String> getDependantTasksIdsFor(final String processInstanceId) {
    return taskStore.getTaskIdsByProcessInstanceId(processInstanceId);
  }

  private Optional<ProcessInstanceEntity> getById(final String processInstanceId) {
    try {
      final SearchRequest searchRequest =
          new SearchRequest(processInstanceIndex.getFullQualifiedName());
      final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
      sourceBuilder.query(
          QueryBuilders.termQuery(TasklistProcessInstanceIndex.ID, processInstanceId));
      searchRequest.source(sourceBuilder);

      final SearchResponse searchResponse = tenantAwareClient.search(searchRequest);
      final SearchHits hits = searchResponse.getHits();
      if (hits.getTotalHits().value == 0) {
        return Optional.empty();
      }

      final SearchHit hit = hits.getAt(0);
      final String sourceAsString = hit.getSourceAsString();
      final ProcessInstanceEntity entity =
          objectMapper.readValue(sourceAsString, ProcessInstanceEntity.class);
      return Optional.of(entity);
    } catch (final IOException e) {
      LOGGER.error(
          String.format("Error retrieving processInstance with ID [%s]", processInstanceId), e);
      return Optional.empty();
    }
  }

  private boolean deleteVariablesFor(final List<String> taskIds) {
    return retryElasticsearchClient.deleteDocumentsByQuery(
        ElasticsearchUtil.whereToSearch(taskVariableTemplate, ALL),
        termsQuery(TasklistTaskVariableTemplate.TASK_ID, taskIds));
  }
}

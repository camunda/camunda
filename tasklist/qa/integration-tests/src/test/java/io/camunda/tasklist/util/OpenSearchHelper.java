/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.TaskEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.v86.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.v86.indices.VariableIndex;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.schema.v86.templates.TaskVariableTemplate;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.GetIndexResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OpenSearchHelper implements NoSqlHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenSearchHelper.class);

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  @Autowired private TaskVariableTemplate taskVariableTemplate;

  @Autowired private VariableIndex variableIndex;

  @Autowired
  @Qualifier("tasklistOsClient")
  private OpenSearchClient osClient;

  @Autowired private ObjectMapper objectMapper;

  @Override
  public TaskEntity getTask(final String taskId) {
    try {
      final GetResponse<TaskEntity> response =
          osClient.get(g -> g.index(taskTemplate.getAlias()).id(taskId), TaskEntity.class);
      if (response.found()) {
        return response.source();
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
  public ProcessInstanceEntity getProcessInstance(final String processInstanceId) {
    try {
      final GetResponse<ProcessInstanceEntity> response =
          osClient.get(
              g -> g.index(processInstanceIndex.getAlias()).id(processInstanceId),
              ProcessInstanceEntity.class);
      if (response.found()) {
        return response.source();
      } else {
        throw new NotFoundApiException(
            String.format("Could not find task for processInstanceId [%s].", processInstanceId));
      }
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the process: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<ProcessInstanceEntity> getProcessInstances(final List<String> processInstanceIds) {
    try {
      final SearchResponse<ProcessInstanceEntity> searchResponse =
          osClient.search(
              s ->
                  s.index(processInstanceIndex.getAlias())
                      .query(q -> q.ids(ids -> ids.values(processInstanceIds))),
              ProcessInstanceEntity.class);
      return searchResponse.hits().hits().stream()
          .map(m -> m.source())
          .collect(Collectors.toList());
    } catch (final IOException e) {
      final String message =
          String.format(
              "Exception occurred, while obtaining list of processes: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<TaskEntity> getTask(final String processInstanceId, final String flowNodeBpmnId) {
    final Query.Builder piId = new Query.Builder();
    if (processInstanceId != null) {
      piId.term(
          t -> t.field(TaskTemplate.PROCESS_INSTANCE_ID).value(FieldValue.of(processInstanceId)));
    }

    final Query.Builder flowQ = new Query.Builder();
    flowQ.term(t -> t.field(TaskTemplate.FLOW_NODE_BPMN_ID).value(FieldValue.of(flowNodeBpmnId)));

    try {
      final Query query;
      if (processInstanceId != null) {
        query = OpenSearchUtil.joinWithAnd(piId, flowQ);
      } else {
        query = flowQ.build();
      }

      final SearchResponse<TaskEntity> response =
          osClient.search(
              s ->
                  s.index(List.of(taskTemplate.getAlias()))
                      .query(query)
                      .sort(
                          sort ->
                              sort.field(
                                  field ->
                                      field
                                          .field(TaskTemplate.CREATION_TIME)
                                          .order(SortOrder.Desc))),
              TaskEntity.class);

      if (response.hits().total().value() >= 1) {
        return response.hits().hits().stream().map(m -> m.source()).collect(Collectors.toList());
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
    final Query.Builder taskIdQ = new Query.Builder();
    taskIdQ.term(term -> term.field(TaskVariableTemplate.TASK_ID).value(FieldValue.of(taskId)));

    final Query.Builder varNameQ = new Query.Builder();
    varNameQ.term(term -> term.field(TaskVariableTemplate.NAME).value(FieldValue.of(varName)));

    try {
      final SearchResponse<TaskVariableEntity> response =
          osClient.search(
              s -> s.query(OpenSearchUtil.joinWithAnd(taskIdQ, varNameQ)),
              TaskVariableEntity.class);
      return response.hits().total().value() > 0;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining all variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public boolean checkVariablesExist(final String[] varNames) {
    try {
      final SearchResponse<VariableEntity> response =
          osClient.search(
              search ->
                  search.query(
                      q ->
                          q.constantScore(
                              cs ->
                                  cs.filter(
                                      filter ->
                                          filter.terms(
                                              terms ->
                                                  terms
                                                      .field(VariableIndex.NAME)
                                                      .terms(
                                                          tv ->
                                                              tv.value(
                                                                  Arrays.stream(varNames)
                                                                      .map(m -> FieldValue.of(m))
                                                                      .collect(
                                                                          Collectors
                                                                              .toList()))))))),
              VariableEntity.class);
      return response.hits().total().value() == varNames.length;
    } catch (final IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining variables: %s", e.getMessage());
      throw new TasklistRuntimeException(message, e);
    }
  }

  @Override
  public List<String> getIdsFromIndex(
      final String idFieldName, final String index, final List<String> ids) {
    final Query q =
        new Query.Builder()
            .terms(
                terms ->
                    terms
                        .field(idFieldName)
                        .terms(
                            t ->
                                t.value(
                                    ids.stream()
                                        .map(m -> FieldValue.of(m))
                                        .collect(Collectors.toList()))))
            .build();

    final SearchRequest.Builder request =
        new SearchRequest.Builder().index(List.of(index)).query(q).size(100);

    try {
      final List<String> idsFromEls =
          OpenSearchUtil.scrollFieldToList(request, idFieldName, osClient);
      return idsFromEls;
    } catch (final IOException e) {
      throw new TasklistRuntimeException(e);
    }
  }

  @Override
  public List<TaskEntity> getTasksFromIdAndIndex(final String index, final List<String> ids) {
    try {
      final SearchResponse<TaskEntity> response =
          osClient.search(
              search -> search.index(index).query(q -> q.ids(idsQ -> idsQ.values(ids))),
              TaskEntity.class);
      return response.hits().hits().stream().map(m -> m.source()).collect(Collectors.toList());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public List<TaskEntity> getAllTasks(final String index) {
    try {
      final SearchResponse<TaskEntity> response =
          osClient.search(
              s -> s.index(index).query(q -> q.matchAll(ma -> ma.queryName("getAll"))),
              TaskEntity.class);
      return response.hits().hits().stream().map(m -> m.source()).collect(Collectors.toList());
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Long countIndexResult(final String index) {
    try {
      final SearchResponse<Object> result =
          osClient.search(
              s -> s.index(index).query(q -> q.matchAll(ma -> ma.queryName("matchall"))),
              Object.class);
      return result.hits().total().value();
    } catch (final IOException e) {
      return -1L;
    }
  }

  @Override
  public Boolean isIndexDynamicMapping(final IndexDescriptor index, final String dynamics)
      throws IOException {
    final GetIndexResponse response =
        osClient.indices().get(i -> i.index(index.getFullQualifiedName()));
    return response
        .get(index.getFullQualifiedName())
        .mappings()
        .dynamic()
        .jsonValue()
        .equals(dynamics);
  }

  @Override
  public Map<String, Object> getFieldDescription(final IndexDescriptor indexDescriptor)
      throws IOException {
    final GetIndexResponse response =
        osClient.indices().get(g -> g.index(indexDescriptor.getFullQualifiedName()));
    return Collections.unmodifiableMap(
        response.get(indexDescriptor.getFullQualifiedName()).mappings().properties());
  }

  @Override
  public Boolean indexHasAlias(final String index, final String alias) throws IOException {
    final GetIndexResponse response = osClient.indices().get(g -> g.index(index));
    return response.get(index).aliases().size() == 1
        && response.get(index).aliases().containsKey(alias);
  }

  @Override
  public void delete(final String index, final String id) throws IOException {
    osClient.delete(d -> d.index(index).id(id));
  }

  @Override
  public void update(final String index, final String id, final Map<String, Object> jsonMap)
      throws IOException {
    osClient.update(u -> u.index(index).id(id).doc(jsonMap), Object.class);
  }
}

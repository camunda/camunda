/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import static io.zeebe.tasklist.util.ElasticsearchUtil.joinWithAnd;
import static io.zeebe.tasklist.util.ElasticsearchUtil.mapSearchHits;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.tasklist.entities.TaskEntity;
import io.zeebe.tasklist.entities.TaskState;
import io.zeebe.tasklist.entities.WorkflowEntity;
import io.zeebe.tasklist.es.schema.indices.WorkflowIndex;
import io.zeebe.tasklist.es.schema.templates.TaskTemplate;
import io.zeebe.tasklist.exceptions.TasklistRuntimeException;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.webapp.es.cache.WorkflowReader;
import io.zeebe.tasklist.webapp.es.reader.TaskReader;
import io.zeebe.tasklist.webapp.rest.exception.NotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(
    prefix = TasklistProperties.PREFIX,
    name = "webappEnabled",
    havingValue = "true",
    matchIfMissing = true)
public class ElasticsearchChecks {

  public static final String WORKFLOW_IS_DEPLOYED_CHECK = "workflowIsDeployedCheck";
  public static final String TASK_IS_CREATED_CHECK = "taskIsCreatedCheck";
  public static final String TASK_IS_COMPLETED_CHECK = "taskIsCompletedCheck";
  private static final Logger LOGGER = LoggerFactory.getLogger(TaskReader.class);

  @Autowired private TaskReader taskReader;

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TaskTemplate taskTemplate;

  @Autowired private WorkflowIndex workflowIndex;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WorkflowReader workflowReader;

  /** Checks whether the workflow of given args[0] workflowId (Long) is deployed. */
  @Bean(name = WORKFLOW_IS_DEPLOYED_CHECK)
  public TestCheck getWorkflowIsDeployedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return WORKFLOW_IS_DEPLOYED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(1);
        assertThat(objects[0]).isInstanceOf(String.class);
        final String workflowId = (String) objects[0];
        try {
          final WorkflowEntity workflow = workflowReader.getWorkflow(workflowId);
          return workflow != null;
        } catch (TasklistRuntimeException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] workflowInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state CREATED.
   */
  @Bean(name = TASK_IS_CREATED_CHECK)
  public TestCheck getTaskIsCreatedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_CREATED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String workflowInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity = getTask(workflowInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.CREATED);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  /**
   * Checks whether the task for given args[0] workflowInstanceKey (Long) and given args[1]
   * flowNodeBpmnId (String) exists and is in state COMPLETED.
   */
  @Bean(name = TASK_IS_COMPLETED_CHECK)
  public Predicate<Object[]> getTaskIsCompletedCheck() {
    return new TestCheck() {
      @Override
      public String getName() {
        return TASK_IS_COMPLETED_CHECK;
      }

      @Override
      public boolean test(final Object[] objects) {
        assertThat(objects).hasSize(2);
        assertThat(objects[0]).isInstanceOf(String.class);
        assertThat(objects[1]).isInstanceOf(String.class);
        final String workflowInstanceKey = (String) objects[0];
        final String flowNodeBpmnId = (String) objects[1];
        try {
          final List<TaskEntity> taskEntity = getTask(workflowInstanceKey, flowNodeBpmnId);
          return taskEntity.stream()
              .map(TaskEntity::getState)
              .collect(Collectors.toList())
              .contains(TaskState.COMPLETED);
        } catch (NotFoundException ex) {
          return false;
        }
      }
    };
  }

  private List<TaskEntity> getTask(String workflowInstanceId, String flowNodeBpmnId) {
    final SearchRequest searchRequest =
        new SearchRequest(taskTemplate.getAlias())
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(TaskTemplate.WORKFLOW_INSTANCE_ID, workflowInstanceId),
                            termQuery(TaskTemplate.FLOW_NODE_BPMN_ID, flowNodeBpmnId))));

    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      if (response.getHits().totalHits >= 1) {
        return mapSearchHits(response.getHits().getHits(), objectMapper, TaskEntity.class);
      } else {
        throw new NotFoundException(
            String.format(
                "Could not find  task for workflowInstanceKey [] with flowNodeBpmnId [%s].",
                workflowInstanceId, flowNodeBpmnId));
      }
    } catch (IOException e) {
      final String message =
          String.format("Exception occurred, while obtaining the workflow: %s", e.getMessage());
      LOGGER.error(message, e);
      throw new TasklistRuntimeException(message, e);
    }
  }

  public interface TestCheck extends Predicate<Object[]> {
    String getName();
  }
}

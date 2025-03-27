/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.RestAPITestUtil.*;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.operate.util.ElasticsearchUtil.scroll;
import static io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.store.elasticsearch.ElasticsearchIncidentStore;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.*;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.webapps.schema.descriptors.operate.index.DecisionIndex;
import io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.PostImporterQueueTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@Conditional(ElasticsearchCondition.class)
@ConditionalOnProperty(
    prefix = OperateProperties.PREFIX,
    name = "webappEnabled",
    havingValue = "true",
    matchIfMissing = true)
public class ElasticsearchChecks {

  @Autowired UserTaskReader userTaskReader;
  @Autowired private RestHighLevelClient esClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ProcessReader processReader;
  @Autowired private ProcessInstanceReader processInstanceReader;
  @Autowired private FlowNodeInstanceReader flowNodeInstanceReader;

  @Autowired private ListenerReader listenerReader;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private EventTemplate eventTemplate;

  @Autowired
  @Qualifier("operateVariableTemplate")
  private VariableTemplate variableTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private PostImporterQueueTemplate postImporterQueueTemplate;

  @Autowired private ListViewReader listViewReader;

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private IncidentReader incidentReader;

  @Autowired private VariableReader variableReader;

  /**
   * Checks whether the process of given args[0] processDefinitionKey (Long) is deployed.
   *
   * @return
   */
  @Bean(name = "processIsDeployedCheck")
  public Predicate<Object[]> getProcessIsDeployedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processDefinitionKey = (Long) objects[0];
      try {
        final ProcessEntity process = processReader.getProcess(processDefinitionKey);
        return process != null;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the process of given args[0] processDefinitionKey (Long) is deployed.
   *
   * @return
   */
  @Bean(name = "decisionsAreDeployedCheck")
  public Predicate<Object[]> getDecisionsAreDeployedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Integer.class);
      final int count = (Integer) objects[0];
      try {
        final int docCount =
            io.camunda.operate.qa.util.ElasticsearchUtil.getDocCount(
                esClient, decisionIndex.getAlias());
        return docCount == count;
      } catch (final IOException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the process of given args[0] processDefinitionKey (Long) is deployed.
   *
   * @return
   */
  @Bean(name = "decisionInstancesAreCreated")
  public Predicate<Object[]> getDecisionInstancesAreCreated() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Integer.class);
      final int count = (Integer) objects[0];
      try {
        final int docCount =
            io.camunda.operate.qa.util.ElasticsearchUtil.getDocCount(
                esClient, decisionInstanceTemplate.getAlias());
        return docCount == count;
      } catch (final IOException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId
   * (String) is in state ACTIVE
   *
   * @return
   */
  @Bean(name = "flowNodeIsActiveCheck")
  public Predicate<Object[]> getFlowNodeIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          if (flowNodes.get(0).getState() == null) {
            return false;
          } else {
            return flowNodes.get(0).getState().equals(FlowNodeState.ACTIVE);
          }
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "eventIsImportedCheck")
  public Predicate<Object[]> getEventIsImportedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String jobType = (String) objects[1];
      final List<EventEntity> events = getAllEvents(processInstanceKey);
      return events.stream()
              .filter(
                  e ->
                      e.getMetadata() != null
                          && e.getMetadata().getJobType() != null
                          && e.getMetadata().getJobType().equals(jobType))
              .count()
          > 0;
    };
  }

  @Bean(name = "eventIsImportedForFlowNodeCheck")
  public Predicate<Object[]> getEventIsImportedForFlowNodeCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(EventType.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      final EventType eventType = (EventType) objects[2];
      final List<EventEntity> events = getAllEvents(processInstanceKey);
      return events.stream()
          .anyMatch(
              e ->
                  Objects.equals(flowNodeId, e.getFlowNodeId())
                      && Objects.equals(eventType, e.getEventType()));
    };
  }

  @Bean(name = "jobWithRetriesCheck")
  public Predicate<Object[]> getJobWithRetriesCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Long.class);
      assertThat(objects[2]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final Long jobKey = (Long) objects[1];
      final Integer numberOfRetriesLeft = (Integer) objects[2];
      final List<EventEntity> events = getAllEvents(processInstanceKey);
      return events.stream()
              .filter(
                  e ->
                      e.getMetadata() != null
                          && e.getMetadata().getJobKey() != null
                          && e.getMetadata().getJobKey().equals(jobKey)
                          && e.getMetadata().getJobRetries().equals(numberOfRetriesLeft))
              .count()
          > 0;
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId
   * (String) is in incident state.
   *
   * @return
   */
  @Bean(name = "flowNodeIsInIncidentStateCheck")
  public Predicate<Object[]> getFlowNodeIsInIncidentStateCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.get(0).getState().equals(FlowNodeState.ACTIVE)
              && flowNodes.get(0).isIncident();
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId
   * (String) is in state TERMINATED
   *
   * @return
   */
  @Bean(name = "flowNodeIsTerminatedCheck")
  public Predicate<Object[]> getFlowNodeIsTerminatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream()
              .allMatch(flowNode -> flowNode.getState().equals(FlowNodeState.TERMINATED));
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "flowNodesAreTerminatedCheck")
  public Predicate<Object[]> getFlowNodesAreTerminatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      final Integer instancesCount = (Integer) objects[2];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream()
                  .filter(fn -> fn.getState().equals(FlowNodeState.TERMINATED))
                  .count()
              >= instancesCount;
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow node of given args[0] processInstanceKey (Long) and args[1] flowNodeId
   * (String) is in state COMPLETED
   *
   * @return
   */
  @Bean(name = "flowNodeIsCompletedCheck")
  public Predicate<Object[]> getFlowNodeIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream()
              .map(FlowNodeInstanceEntity::getState)
              .anyMatch(fns -> fns.equals(FlowNodeState.COMPLETED));
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the flow nodes of given args[0] processInstanceKey (Long) and args[1] flowNodeId
   * (String) is in state COMPLETED and the amount of such flow node instances is args[2]
   *
   * @return
   */
  @Bean(name = "flowNodesAreCompletedCheck")
  public Predicate<Object[]> getFlowNodesAreCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      final Integer instancesCount = (Integer) objects[2];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream()
                  .filter(fn -> fn.getState().equals(FlowNodeState.COMPLETED))
                  .count()
              >= instancesCount;
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "flowNodesAreActiveCheck")
  public Predicate<Object[]> getFlowNodesAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      final Integer instancesCount = (Integer) objects[2];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream().filter(fn -> fn.getState().equals(FlowNodeState.ACTIVE)).count()
              >= instancesCount;
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "flowNodesExistCheck")
  public Predicate<Object[]> getFlowNodesExistCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final String flowNodeId = (String) objects[1];
      final Integer instancesCount = (Integer) objects[2];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances =
            getAllFlowNodeInstances(processInstanceKey);
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        return flowNodes.size() >= instancesCount;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "flowNodesInAnyInstanceAreActiveCheck")
  public Predicate<Object[]> getFlowNodesInAnyInstanceAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(String.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      final String flowNodeId = (String) objects[0];
      final Integer instancesCount = (Integer) objects[1];
      try {
        final List<FlowNodeInstanceEntity> flowNodeInstances = getAllFlowNodeInstances();
        final List<FlowNodeInstanceEntity> flowNodes =
            flowNodeInstances.stream()
                .filter(a -> a.getFlowNodeId().equals(flowNodeId))
                .collect(Collectors.toList());
        if (flowNodes.size() == 0) {
          return false;
        } else {
          return flowNodes.stream().filter(fn -> fn.getState().equals(FlowNodeState.ACTIVE)).count()
              >= instancesCount;
        }
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(constantScoreQuery(processInstanceKeyQuery))
                    .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<EventEntity> getAllEvents(final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(eventTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(processInstanceKeyQuery)));
    try {
      return scroll(searchRequest, EventEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances() {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(flowNodeInstanceTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(matchAllQuery())
                    .sort(FlowNodeInstanceTemplate.POSITION, SortOrder.ASC));
    try {
      return scroll(searchRequest, FlowNodeInstanceEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Checks whether variable of given args[0] processInstanceKey (Long) and args[1] scopeKey(Long)
   * and args[2] varName (String) exists
   *
   * @return
   */
  @Bean(name = "variableExistsCheck")
  public Predicate<Object[]> getVariableExistsCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String varName = (String) objects[1];
      try {
        final List<VariableEntity> variables = getAllVariables(processInstanceKey);
        return variables.stream().anyMatch(v -> v.getName().equals(varName));
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "variableExistsInCheck")
  public Predicate<Object[]> getVariableExistsInCheck() {
    return objects -> {
      assertThat(objects).hasSize(3);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      assertThat(objects[2]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      final String varName = (String) objects[1];
      final Long scopeKey = (Long) objects[2];
      try {
        return null
            != variableReader.getVariableByName(processInstanceKey + "", scopeKey + "", varName);
      } catch (final OperateRuntimeException ex) {
        return false;
      }
    };
  }

  @Bean(name = "variableHasValue")
  public Predicate<Object[]> getVariableHasValue() {
    return objects -> {
      assertThat(objects).hasSize(4);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String varName = (String) objects[1];
      final Object value = objects[2];
      final Long scopeKey = (Long) objects[3];
      try {
        return value.equals(
            variableReader
                .getVariableByName(processInstanceKey + "", "" + scopeKey, varName)
                .getValue());
      } catch (final OperateRuntimeException ex) {
        return false;
      }
    };
  }

  public List<VariableEntity> getAllVariables(final Long processInstanceKey) {
    final TermQueryBuilder processInstanceKeyQuery =
        termQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceKey);
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(variableTemplate)
            .source(new SearchSourceBuilder().query(constantScoreQuery(processInstanceKeyQuery)));
    try {
      return scroll(searchRequest, VariableEntity.class, objectMapper, esClient);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Checks whether variable of given args[0] processInstanceKey (Long) and args[1] scopeKey (Long)
   * and args[2] varName (String) with args[3] (String) value exists
   *
   * @return
   */
  @Bean(name = "variableEqualsCheck")
  public Predicate<Object[]> getVariableEqualsCheck() {
    return objects -> {
      assertThat(objects).hasSize(4);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Long.class);
      assertThat(objects[2]).isInstanceOf(String.class);
      assertThat(objects[3]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final Long scopeKey = (Long) objects[1];
      final String varName = (String) objects[2];
      final String varValue = (String) objects[3];
      try {
        final List<VariableDto> variables = getVariables(processInstanceKey, scopeKey);
        return variables.stream()
            .anyMatch(v -> v.getName().equals(varName) && v.getValue().equals(varValue));
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  private List<VariableDto> getVariables(final Long processInstanceKey, final Long scopeKey) {
    return variableReader.getVariables(
        String.valueOf(processInstanceKey),
        new VariableRequestDto().setScopeId(String.valueOf(scopeKey)));
  }

  /**
   * Checks whether any incidents is active in processInstance of given processInstanceKey (Long)
   *
   * @return
   */
  @Bean(name = "incidentIsActiveCheck")
  public Predicate<Object[]> getIncidentIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      try {
        final List<FlowNodeInstanceEntity> allActivityInstances =
            getAllFlowNodeInstances(processInstanceKey);
        boolean found = allActivityInstances.stream().anyMatch(ai -> ai.isIncident());
        if (found) {
          final List<IncidentEntity> allIncidents =
              incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
          found = allIncidents.size() > 0;
        }
        return found;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether an incident with given error message (String arg[1]) is active in
   * processInstance of given processInstanceKey (Long arg[0])
   *
   * @return
   */
  @Bean(name = "incidentWithErrorMessageIsActiveCheck")
  public Predicate<Object[]> getIncidentWithErrorMessageIsActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final Long processInstanceKey = (Long) objects[0];
      final String errorMessage = (String) objects[1];
      try {
        final List<FlowNodeInstanceEntity> allActivityInstances =
            getAllFlowNodeInstances(processInstanceKey);
        boolean found = allActivityInstances.stream().anyMatch(ai -> ai.isIncident());
        if (found) {
          final List<IncidentEntity> allIncidents =
              incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey);
          found =
              allIncidents.stream().filter(ie -> ie.getErrorMessage().equals(errorMessage)).count()
                  > 0;
        }
        return found;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether given amount of incidents exist and active.
   *
   * @return
   */
  @Bean(name = "incidentsInAnyInstanceAreActiveCheck")
  public Predicate<Object[]> getIncidentsInAnyInstanceAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long count = (Long) objects[0];
      try {
        return getActiveIncidentsCount() == count && getPendingIncidentsCount() == 0;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether given amount of incidents exist and active.
   *
   * @return
   */
  @Bean(name = "postImporterQueueCountCheck")
  public Predicate<Object[]> getPostImporterQueueCountCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Integer.class);
      final Integer count = (Integer) objects[0];
      try {
        return getPostImporterQueueCount() == count;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  public long getActiveIncidentsCount() {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder().query(ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getPendingIncidentsCount() {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(IncidentTemplate.STATE, IncidentState.PENDING)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getPostImporterQueueCount() {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(postImporterQueueTemplate)
            .source(new SearchSourceBuilder().query(matchAllQuery()));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getActiveIncidentsCount(final Long processInstanceKey) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            ElasticsearchIncidentStore.ACTIVE_INCIDENT_QUERY,
                            termQuery(PROCESS_INSTANCE_KEY, processInstanceKey))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getIncidentsCount(final Long processInstanceKey, final IncidentState state) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(STATE, state),
                            termQuery(PROCESS_INSTANCE_KEY, processInstanceKey))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  public long getIncidentsCount(final String bpmnProcessId, final IncidentState state) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(
                        joinWithAnd(
                            termQuery(STATE, state), termQuery(BPMN_PROCESS_ID, bpmnProcessId))));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Including pending
   *
   * @param processInstanceKey
   * @return
   */
  public long getIncidentsCount(final Long processInstanceKey) {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(PROCESS_INSTANCE_KEY, processInstanceKey)));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Including pending
   *
   * @return
   */
  public long getIncidentsCount() {
    final SearchRequest searchRequest =
        ElasticsearchUtil.createSearchRequest(incidentTemplate)
            .source(new SearchSourceBuilder().query(matchAllQuery()));
    try {
      final SearchResponse response = esClient.search(searchRequest, RequestOptions.DEFAULT);
      return response.getHits().getTotalHits().value;
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Checks whether the incidents of given args[0] processInstanceKey (Long) equals given args[1]
   * incidentsCount (Integer)
   *
   * @return
   */
  @Bean(name = "incidentsAreActiveCheck")
  public Predicate<Object[]> getIncidentsAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final int incidentsCount = (int) objects[1];
      try {
        return getActiveIncidentsCount(processInstanceKey) == incidentsCount;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the incidents of given args[0] bpmnProcessId (Long) equals given args[1]
   * incidentsCount (Integer)
   *
   * @return
   */
  @Bean(name = "incidentsInProcessAreActiveCheck")
  public Predicate<Object[]> incidentsInProcessAreActiveCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(String.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      final String bpmnProcessId = (String) objects[0];
      final int incidentsCount = (int) objects[1];
      try {
        return getIncidentsCount(bpmnProcessId, IncidentState.ACTIVE) == incidentsCount;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the incidents of given args[0] processInstanceKey (Long) are present, no matter
   * pending or not. Ammount of incidents: args[1] incidentsCount (Integer).
   *
   * @return
   */
  @Bean(name = "incidentsArePresentCheck")
  public Predicate<Object[]> getIncidentsArePresentCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final int incidentsCount = (int) objects[1];
      try {
        return getIncidentsCount(processInstanceKey) == incidentsCount;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the incidents are present, no matter pending or not. Ammount of incidents:
   * args[1] incidentsCount (Integer).
   *
   * @return
   */
  @Bean(name = "incidentsInAnyInstanceArePresentCheck")
  public Predicate<Object[]> getIncidentsInAnyInstanceArePresentCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Integer.class);
      final int incidentsCount = (int) objects[0];
      try {
        return getIncidentsCount() == incidentsCount;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether there are no incidents in activities exists in given processInstanceKey (Long)
   *
   * @return
   */
  @Bean(name = "noActivitiesHaveIncident")
  public Predicate<Object[]> getNoActivitiesHaveIncident() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      try {
        final List<FlowNodeInstanceEntity> allActivityInstances =
            getAllFlowNodeInstances(processInstanceKey);
        return allActivityInstances.stream().noneMatch(ai -> ai.isIncident());
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether there is a given amount of resolved incidents for given processInstanceKey.
   *
   * @return
   */
  @Bean(name = "incidentsAreResolved")
  public Predicate<Object[]> getIncidentsAreResolved() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      final Long processInstanceKey = (Long) objects[0];
      final int resolvedIncidentsCount = (int) objects[1];
      try {
        return getIncidentsCount(processInstanceKey, IncidentState.RESOLVED)
            == resolvedIncidentsCount;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the processInstance of given processInstanceKey (Long) is CANCELED.
   *
   * @return
   */
  @Bean(name = "processInstanceIsCanceledCheck")
  public Predicate<Object[]> getProcessInstanceIsCanceledCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      try {
        final ProcessInstanceForListViewEntity instance =
            processInstanceReader.getProcessInstanceByKey(processInstanceKey);
        return instance.getState().equals(ProcessInstanceState.CANCELED);
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the processInstance of given processInstanceKey (Long) is CREATED.
   *
   * @return
   */
  @Bean(name = "processInstanceIsCreatedCheck")
  public Predicate<Object[]> getProcessInstanceIsCreatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      try {
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
        return true;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether the processInstance of given processInstanceKey (Long) is COMPLETED.
   *
   * @return
   */
  @Bean(name = "processInstanceIsCompletedCheck")
  public Predicate<Object[]> getProcessInstanceIsCompletedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      try {
        final ProcessInstanceForListViewEntity instance =
            processInstanceReader.getProcessInstanceByKey(processInstanceKey);
        return instance.getState().equals(ProcessInstanceState.COMPLETED);
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  /**
   * Checks whether all processInstances from given processInstanceKeys (List<Long>) are finished
   *
   * @return
   */
  @Bean(name = "processInstancesAreFinishedCheck")
  public Predicate<Object[]> getProcessInstancesAreFinishedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      final List<Long> ids = (List<Long>) objects[0];
      final ListViewRequestDto getFinishedRequest =
          createGetAllFinishedRequest(q -> q.setIds(CollectionUtil.toSafeListOfStrings(ids)));
      getFinishedRequest.setPageSize(ids.size());
      final ListViewResponseDto responseDto =
          listViewReader.queryProcessInstances(getFinishedRequest);
      return responseDto.getTotalCount() == ids.size();
    };
  }

  /**
   * Checks whether given amount (argument #2) of processInstances with given processDefinitionId
   * (argument #1) are started
   *
   * @return
   */
  @Bean(name = "processInstancesAreStartedByProcessIdCheck")
  public Predicate<Object[]> getProcessInstancesAreStartedByProcessIdCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(Integer.class);
      final Long processDefinitionId = (Long) objects[0];
      final Integer count = (Integer) objects[1];
      final ListViewRequestDto getActiveRequest =
          createProcessInstanceRequest(
              q -> {
                q.setProcessIds(CollectionUtil.toSafeListOfStrings(processDefinitionId));
                q.setRunning(true);
                q.setActive(true);
                q.setIncidents(true);
              });
      getActiveRequest.setPageSize(count);
      final ListViewResponseDto responseDto =
          listViewReader.queryProcessInstances(getActiveRequest);
      return responseDto.getTotalCount() == count;
    };
  }

  /**
   * Checks whether all processInstances from given processInstanceKeys (List<Long>) are started
   *
   * @return
   */
  @Bean(name = "processInstancesAreStartedCheck")
  public Predicate<Object[]> getProcessInstancesAreStartedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      final List<Long> ids = (List<Long>) objects[0];
      final ListViewRequestDto getActiveRequest =
          createProcessInstanceRequest(
              q -> {
                q.setIds(CollectionUtil.toSafeListOfStrings(ids));
                q.setRunning(true);
                q.setActive(true);
                q.setIncidents(true);
              });
      getActiveRequest.setPageSize(ids.size());
      final ListViewResponseDto responseDto =
          listViewReader.queryProcessInstances(getActiveRequest);
      return responseDto.getTotalCount() == ids.size();
    };
  }

  /**
   * Checks whether all processInstances from given processInstanceKeys (List<Long>) exist
   *
   * @return
   */
  @Bean(name = "processInstanceExistsCheck")
  public Predicate<Object[]> getProcessInstanceExistsCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(List.class);
      @SuppressWarnings("unchecked")
      final List<Long> ids = (List<Long>) objects[0];
      final ListViewRequestDto getActiveRequest =
          createGetAllProcessInstancesRequest(
              q -> {
                q.setIds(CollectionUtil.toSafeListOfStrings(ids));
              });
      getActiveRequest.setPageSize(ids.size());
      final ListViewResponseDto responseDto =
          listViewReader.queryProcessInstances(getActiveRequest);
      return responseDto.getTotalCount() == ids.size();
    };
  }

  /**
   * Checks whether all operations for given processInstanceKey (Long) are completed
   *
   * @return
   */
  @Bean(name = "operationsByProcessInstanceAreCompletedCheck")
  public Predicate<Object[]> getOperationsByProcessInstanceAreCompleted() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      final ListViewProcessInstanceDto processInstance =
          processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
      return processInstance.getOperations().stream()
          .allMatch(
              operation -> {
                return operation.getState().equals(OperationState.COMPLETED);
              });
    };
  }

  /**
   * Checks whether all operations for given processInstanceKey (Long) are completed
   *
   * @return
   */
  @Bean(name = "operationsByProcessInstanceAreFailedCheck")
  public Predicate<Object[]> getOperationsByProcessInstanceAreFailed() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Long.class);
      final Long processInstanceKey = (Long) objects[0];
      final ListViewProcessInstanceDto processInstance =
          processInstanceReader.getProcessInstanceWithOperationsByKey(processInstanceKey);
      return processInstance.getOperations().stream()
          .allMatch(
              operation -> {
                return operation.getState().equals(OperationState.FAILED);
              });
    };
  }

  @Bean(name = "userTasksAreCreated")
  public Predicate<Object[]> getUserTaskIsImportedCheck() {
    return objects -> {
      assertThat(objects).hasSize(1);
      assertThat(objects[0]).isInstanceOf(Integer.class);
      final Integer count = (Integer) objects[0];
      try {
        final List<TaskEntity> userTasks = userTaskReader.getUserTasks();
        return userTasks.size() == count;
      } catch (final NotFoundException ex) {
        return false;
      }
    };
  }

  @Bean(name = "listenerJobIsCreated")
  public Predicate<Object[]> getListenerJobIsCreatedCheck() {
    return objects -> {
      assertThat(objects).hasSize(2);
      assertThat(objects[0]).isInstanceOf(Long.class);
      assertThat(objects[1]).isInstanceOf(String.class);
      final long processInstanceId = (long) objects[0];
      final String flowNodeId = (String) objects[1];
      final ListenerRequestDto dto = new ListenerRequestDto().setFlowNodeId(flowNodeId);
      return listenerReader
              .getListenerExecutions(Long.toString(processInstanceId), dto)
              .getTotalCount()
          > 0;
    };
  }
}

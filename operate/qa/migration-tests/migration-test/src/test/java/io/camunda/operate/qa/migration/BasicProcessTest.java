/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration;

import static io.camunda.operate.qa.migration.util.TestConstants.DEFAULT_TENANT_ID;
import static io.camunda.operate.util.CollectionUtil.chooseOne;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.*;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.v110.BasicProcessDataGenerator;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.webapps.schema.descriptors.operate.template.EventTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.operate.EventEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.webapps.schema.entities.operate.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.operate.post.PostImporterActionType;
import io.camunda.webapps.schema.entities.operate.post.PostImporterQueueEntity;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.search.SearchRequest;
import org.junit.Before;
import org.junit.Test;

public class BasicProcessTest extends AbstractMigrationTest {

  private final String bpmnProcessId = BasicProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> processInstanceIds;

  @Before
  public void findProcessInstanceIds() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    if (processInstanceIds == null) {
      // sleepFor(5_000);
      final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
      // Process instances list
      searchRequest
          .source()
          .query(
              joinWithAnd(
                  termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                  termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
      try {
        processInstanceIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(processInstanceIds).hasSize(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT);
    }
  }

  @Test
  public void testImportPositions() {
    final List<ImportPositionEntity> importPositions =
        entityReader.getEntitiesFor(importPositionIndex.getAlias(), ImportPositionEntity.class);
    assertThat(importPositions.isEmpty())
        .describedAs("There should exists at least 1 ImportPosition")
        .isFalse();
  }

  @Test
  public void testUsageMetrics() {
    final List<MetricEntity> metrics =
        entityReader.getEntitiesFor(metricIndex.getAlias(), MetricEntity.class);
    assertThat(metrics.stream().allMatch(m -> m.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All events have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testProcess() {
    final SearchRequest searchRequest = new SearchRequest(processTemplate.getAlias());
    searchRequest.source().query(termQuery(EventTemplate.BPMN_PROCESS_ID, bpmnProcessId));
    final List<ProcessEntity> processes =
        entityReader.searchEntitiesFor(searchRequest, ProcessEntity.class);
    assertThat(processes).hasSize(1);
    assertThat(processes.get(0).getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
  }

  @Test
  public void testEvents() {
    final SearchRequest searchRequest = new SearchRequest(eventTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(EventTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    final List<EventEntity> events =
        entityReader.searchEntitiesFor(searchRequest, EventEntity.class);
    assertThat(events.isEmpty()).isFalse();
    assertThat(events.stream().filter(e -> e.getMetadata() != null).count())
        .describedAs("At least one event has metadata")
        .isGreaterThan(0);
    assertThat(events.stream().allMatch(e -> e.getEventSourceType() != null))
        .describedAs("All events have a EventSourceType")
        .isTrue();
    assertThat(events.stream().allMatch(e -> e.getEventType() != null))
        .describedAs("All events have a EventType")
        .isTrue();
    assertThat(events.stream().allMatch(e -> e.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All events have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testSequenceFlows() {
    final SearchRequest searchRequest = new SearchRequest(sequenceFlowTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    final List<SequenceFlowEntity> sequenceFlows =
        entityReader.searchEntitiesFor(searchRequest, SequenceFlowEntity.class);
    assertThat(sequenceFlows.size())
        .isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 2);
    assertThat(sequenceFlows.stream().allMatch(sf -> sf.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All sequence flows have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testFlowNodeInstances() {
    final SearchRequest searchRequest = new SearchRequest(flowNodeInstanceTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    final List<FlowNodeInstanceEntity> flowNodeInstances =
        entityReader.searchEntitiesFor(searchRequest, FlowNodeInstanceEntity.class);
    assertThat(flowNodeInstances.size())
        .isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 3);
    assertThat(flowNodeInstances.stream().allMatch(a -> a.getType() != null))
        .as("All flow node instances have a type")
        .isTrue();
    assertThat(flowNodeInstances.stream().allMatch(a -> a.getState() != null))
        .as("All flow node instances have a state")
        .isTrue();
    assertThat(flowNodeInstances.stream().allMatch(a -> a.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All flow node instances have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testVariables() {
    final SearchRequest searchRequest = new SearchRequest(variableTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    final List<VariableEntity> variableEntities =
        entityReader.searchEntitiesFor(searchRequest, VariableEntity.class);
    assertThat(variableEntities.size())
        .isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 4);
    assertThat(variableEntities.stream().allMatch(v -> v.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All variables have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testOperations() {
    // TODO narrow down the search criteria
    final List<OperationEntity> operations =
        entityReader.getEntitiesFor(operationTemplate.getAlias(), OperationEntity.class);
    assertThat(operations.size()).describedAs("At least one operation is active").isGreaterThan(0);
  }

  @Test
  public void testListViews() {
    final SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    final int processInstancesCount = BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT;

    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    final List<ProcessInstanceForListViewEntity> processInstances =
        entityReader.searchEntitiesFor(searchRequest, ProcessInstanceForListViewEntity.class);

    processInstances.forEach(
        pi -> {
          assertThat(pi.getTreePath()).isNotNull();
          assertThat(pi).matches(p -> p.getTreePath().equals("PI_" + p.getProcessInstanceKey()));
          assertThat(pi.getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
        });

    //  Variables list
    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, VARIABLES_JOIN_RELATION),
                termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    final List<VariableForListViewEntity> variablesList =
        entityReader.searchEntitiesFor(searchRequest, VariableForListViewEntity.class);
    assertThat(variablesList.size()).isEqualTo(processInstancesCount * 4);

    // Activity instances list
    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    final List<FlowNodeInstanceForListViewEntity> activitiesList =
        entityReader.searchEntitiesFor(searchRequest, FlowNodeInstanceForListViewEntity.class);
    assertThat(activitiesList.size()).isEqualTo(processInstancesCount * 3);
  }

  @Test
  public void testIncidents() {
    final SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    final List<IncidentEntity> incidents =
        entityReader.searchEntitiesFor(searchRequest, IncidentEntity.class);
    assertThat(incidents.size())
        .isBetween(
            BasicProcessDataGenerator.INCIDENT_COUNT
                - (BasicProcessDataGenerator.COUNT_OF_CANCEL_OPERATION
                    + BasicProcessDataGenerator.COUNT_OF_RESOLVE_OPERATION),
            BasicProcessDataGenerator.INCIDENT_COUNT);
    assertThat(incidents.stream().allMatch(i -> i.getState() != null))
        .describedAs("Each incident has a state")
        .isTrue();
    assertThat(incidents.stream().allMatch(i -> i.getErrorType() != null))
        .describedAs("Each incident has an errorType")
        .isTrue();
    assertThat(incidents.stream().allMatch(i -> i.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("Each incident has <default> tenant id")
        .isTrue();
    final IncidentEntity randomIncident = chooseOne(incidents);
    assertThat(randomIncident.getErrorMessageHash()).isNotNull();

    incidents.forEach(
        inc -> {
          assertThat(inc.getTreePath()).isNotNull();
          assertThat(inc)
              .matches(
                  i ->
                      i.getTreePath()
                          .equals(
                              "PI_"
                                  + i.getProcessInstanceKey()
                                  + "/FN_"
                                  + i.getFlowNodeId()
                                  + "/FNI_"
                                  + i.getFlowNodeInstanceKey()));
          assertThat(inc.getBpmnProcessId()).isNotNull();
          assertThat(inc.getProcessDefinitionKey()).isNotNull();
        });
  }

  @Test
  public void testPostImporterEntities() {
    final List<PostImporterQueueEntity> postImporterQueueEntities =
        entityReader.getEntitiesFor(
            postImporterQueueTemplate.getAlias(), PostImporterQueueEntity.class);
    final long incidentsCount = entityReader.countEntitiesFor(incidentTemplate.getAlias());
    assertThat(postImporterQueueEntities.size()).isEqualTo(incidentsCount);
    assertThat(postImporterQueueEntities)
        .extracting(PostImporterQueueEntity::getIntent)
        .containsOnly(IncidentIntent.CREATED.name());
    assertThat(postImporterQueueEntities)
        .extracting(PostImporterQueueEntity::getActionType)
        .containsOnly(PostImporterActionType.INCIDENT);
  }

  @Test
  public void testUsers() {
    final List<UserEntity> users =
        entityReader.getEntitiesFor(userIndex.getAlias(), UserEntity.class);
    assertThat(users.size()).isEqualTo(3);
    assertThat(users).extracting(UserIndex.USER_ID).contains("demo", "act", "view");
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration;

import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.util.CollectionUtil.chooseOne;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.SequenceFlowEntity;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.entities.VariableEntity;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.qa.migration.util.AbstractMigrationTest;
import io.camunda.operate.qa.migration.v100.BasicProcessDataGenerator;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.SequenceFlowTemplate;
import io.camunda.operate.schema.templates.VariableTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;

import org.elasticsearch.action.search.SearchRequest;
import org.junit.Before;
import org.junit.Test;

public class BasicProcessTest extends AbstractMigrationTest {

  private String bpmnProcessId = BasicProcessDataGenerator.PROCESS_BPMN_PROCESS_ID;
  private Set<String> processInstanceIds;

  @Before
  public void findProcessInstanceIds() {
    assumeThatProcessIsUnderTest(bpmnProcessId);
    if (processInstanceIds == null) {
      //sleepFor(5_000);
      SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
      // Process instances list
      searchRequest.source()
          .query(joinWithAnd(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION), termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
      try {
        processInstanceIds = ElasticsearchUtil.scrollIdsToSet(searchRequest, esClient);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      assertThat(processInstanceIds).hasSize(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT);
    }
  }

  @Test
  public void testImportPositions() {
    List<ImportPositionEntity> importPositions = entityReader.getEntitiesFor(importPositionIndex.getAlias(), ImportPositionEntity.class);
    assertThat(importPositions.isEmpty()).describedAs("There should exists at least 1 ImportPosition").isFalse();
  }

  @Test
  public void testEvents() {
    SearchRequest searchRequest = new SearchRequest(eventTemplate.getAlias());
    searchRequest.source().query(termsQuery(EventTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<EventEntity> events = entityReader.searchEntitiesFor(searchRequest, EventEntity.class);
    assertThat(events.isEmpty()).isFalse();
    assertThat(events.stream().filter(e -> e.getMetadata() != null).count()).describedAs("At least one event has metadata").isGreaterThan(0);
    assertThat(events.stream().allMatch(e -> e.getEventSourceType()!= null)).describedAs("All events have a EventSourceType").isTrue();
    assertThat(events.stream().allMatch(e -> e.getEventType() != null)).describedAs("All events have a EventType").isTrue();
  }

  @Test
  public void testSequenceFlows() {
    SearchRequest searchRequest = new SearchRequest(sequenceFlowTemplate.getAlias());
    searchRequest.source().query(termsQuery(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<SequenceFlowEntity> sequenceFlows = entityReader.searchEntitiesFor(searchRequest, SequenceFlowEntity.class);
    assertThat(sequenceFlows.size()).isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 2);
  }

  @Test
  public void testFlowNodeInstances() {
    SearchRequest searchRequest = new SearchRequest(flowNodeInstanceTemplate.getAlias());
    searchRequest.source().query(termsQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<FlowNodeInstanceEntity> flowNodeInstances = entityReader.searchEntitiesFor(searchRequest, FlowNodeInstanceEntity.class);
    assertThat(flowNodeInstances.size()).isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 3);
    assertThat(flowNodeInstances.stream().allMatch( a -> a.getType() != null)).as("All flow node instances have a type").isTrue();
    assertThat(flowNodeInstances.stream().allMatch( a -> a.getState()!= null)).as("All flow node instances have a state").isTrue();
  }

  @Test
  public void testVariables() {
    SearchRequest searchRequest = new SearchRequest(variableTemplate.getAlias());
    searchRequest.source().query(termsQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<VariableEntity> variableEntities = entityReader.searchEntitiesFor(searchRequest, VariableEntity.class);
    assertThat(variableEntities.size()).isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 4);
  }

  @Test
  public void testOperations() {
    //TODO narrow down the search criteria
    List<OperationEntity> operations = entityReader.getEntitiesFor(operationTemplate.getAlias(), OperationEntity.class);
    assertThat(operations.size()).describedAs("At least one operation is active").isGreaterThan(0);
  }

  @Test
  public void testListViews() {
    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    int processInstancesCount = BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT;

    searchRequest.source().query(joinWithAnd(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    List<ProcessInstanceForListViewEntity> processInstances = entityReader
        .searchEntitiesFor(searchRequest, ProcessInstanceForListViewEntity.class);

    processInstances.forEach(pi -> {
      assertThat(pi.getTreePath()).isNotNull();
      assertThat(pi).matches(p -> p.getTreePath().equals("PI_" + p.getProcessInstanceKey()));
    });

    //  Variables list
    searchRequest.source().query(joinWithAnd(termQuery(JOIN_RELATION, VARIABLES_JOIN_RELATION),
        termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    List<VariableForListViewEntity> variablesList = entityReader.searchEntitiesFor(searchRequest, VariableForListViewEntity.class);
    assertThat(variablesList.size()).isEqualTo(processInstancesCount * 4);

    // Activity instances list
    searchRequest.source().query(joinWithAnd(termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
        termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    List<FlowNodeInstanceForListViewEntity> activitiesList = entityReader.searchEntitiesFor(searchRequest, FlowNodeInstanceForListViewEntity.class);
    assertThat(activitiesList.size()).isEqualTo(processInstancesCount * 3);
    assertThat(activitiesList).filteredOn(al -> al.getIncidentKeys()!=null && !al.getIncidentKeys().isEmpty()).extracting(PENDING_INCIDENT).containsOnly(true);
    assertThat(activitiesList).filteredOn(al -> al.getIncidentKeys()!=null && !al.getIncidentKeys().isEmpty()).size().isBetween(
        BasicProcessDataGenerator.INCIDENT_COUNT - (BasicProcessDataGenerator.COUNT_OF_CANCEL_OPERATION + BasicProcessDataGenerator.COUNT_OF_RESOLVE_OPERATION),
        BasicProcessDataGenerator.INCIDENT_COUNT
    );
    assertThat(activitiesList).filteredOn(al -> al.getIncidentKeys()==null || al.getIncidentKeys().isEmpty()).extracting(PENDING_INCIDENT).containsOnly(false);
  }

  @Test
  public void testIncidents() {
    SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias());
    searchRequest.source().query(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<IncidentEntity> incidents = entityReader.searchEntitiesFor(searchRequest, IncidentEntity.class);
    assertThat(incidents.size()).isBetween(
        BasicProcessDataGenerator.INCIDENT_COUNT - (BasicProcessDataGenerator.COUNT_OF_CANCEL_OPERATION + BasicProcessDataGenerator.COUNT_OF_RESOLVE_OPERATION),
        BasicProcessDataGenerator.INCIDENT_COUNT
    );
    assertThat(incidents.stream().allMatch(i -> i.getState() != null)).describedAs("Each incident has a state").isTrue();
    assertThat(incidents.stream().allMatch(i -> i.getErrorType() != null)).describedAs("Each incident has an errorType").isTrue();
    IncidentEntity randomIncident = chooseOne(incidents);
    assertThat(randomIncident.getErrorMessageHash()).isNotNull();

    incidents.forEach(inc -> {
      assertThat(inc.getTreePath()).isNotNull();
      assertThat(inc).matches(i -> i.getTreePath().equals(
          "PI_" + i.getProcessInstanceKey() + "/FN_" + i.getFlowNodeId() + "/FNI_" + i
              .getFlowNodeInstanceKey()));
      assertThat(inc.getBpmnProcessId()).isNotNull();
      assertThat(inc.getProcessDefinitionKey()).isNotNull();
    });
  }

  @Test
  public void testUsers() {
    final List<UserEntity> users = entityReader.getEntitiesFor(userIndex.getAlias(), UserEntity.class);
    assertThat(users.size()).isEqualTo(3);
    assertThat(users).extracting(UserIndex.USER_ID).contains("demo", "act", "view");
  }

}

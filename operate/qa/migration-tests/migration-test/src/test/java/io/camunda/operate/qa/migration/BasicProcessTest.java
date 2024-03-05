/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.migration;

import static io.camunda.operate.qa.migration.util.TestConstants.DEFAULT_TENANT_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.*;
import static io.camunda.operate.util.CollectionUtil.chooseOne;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import io.camunda.operate.entities.*;
import io.camunda.operate.entities.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.VariableForListViewEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.entities.post.PostImporterActionType;
import io.camunda.operate.entities.post.PostImporterQueueEntity;
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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
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
      // sleepFor(5_000);
      SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
      // Process instances list
      searchRequest
          .source()
          .query(
              joinWithAnd(
                  termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                  termQuery(ListViewTemplate.BPMN_PROCESS_ID, bpmnProcessId)));
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
    List<ImportPositionEntity> importPositions =
        entityReader.getEntitiesFor(importPositionIndex.getAlias(), ImportPositionEntity.class);
    assertThat(importPositions.isEmpty())
        .describedAs("There should exists at least 1 ImportPosition")
        .isFalse();
  }

  @Test
  public void testUsageMetrics() {
    List<MetricEntity> metrics =
        entityReader.getEntitiesFor(metricIndex.getAlias(), MetricEntity.class);
    assertThat(metrics.stream().allMatch(m -> m.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All events have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testProcess() {
    SearchRequest searchRequest = new SearchRequest(processTemplate.getAlias());
    searchRequest.source().query(termQuery(EventTemplate.BPMN_PROCESS_ID, bpmnProcessId));
    List<ProcessEntity> processes =
        entityReader.searchEntitiesFor(searchRequest, ProcessEntity.class);
    assertThat(processes).hasSize(1);
    assertThat(processes.get(0).getTenantId()).isEqualTo(DEFAULT_TENANT_ID);
  }

  @Test
  public void testEvents() {
    SearchRequest searchRequest = new SearchRequest(eventTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(EventTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<EventEntity> events = entityReader.searchEntitiesFor(searchRequest, EventEntity.class);
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
    SearchRequest searchRequest = new SearchRequest(sequenceFlowTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(SequenceFlowTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<SequenceFlowEntity> sequenceFlows =
        entityReader.searchEntitiesFor(searchRequest, SequenceFlowEntity.class);
    assertThat(sequenceFlows.size())
        .isEqualTo(BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT * 2);
    assertThat(sequenceFlows.stream().allMatch(sf -> sf.getTenantId().equals(DEFAULT_TENANT_ID)))
        .describedAs("All sequence flows have <default> tenant id")
        .isTrue();
  }

  @Test
  public void testFlowNodeInstances() {
    SearchRequest searchRequest = new SearchRequest(flowNodeInstanceTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<FlowNodeInstanceEntity> flowNodeInstances =
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
    SearchRequest searchRequest = new SearchRequest(variableTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(VariableTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<VariableEntity> variableEntities =
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
    List<OperationEntity> operations =
        entityReader.getEntitiesFor(operationTemplate.getAlias(), OperationEntity.class);
    assertThat(operations.size()).describedAs("At least one operation is active").isGreaterThan(0);
  }

  @Test
  public void testListViews() {
    SearchRequest searchRequest = new SearchRequest(listViewTemplate.getAlias());
    int processInstancesCount = BasicProcessDataGenerator.PROCESS_INSTANCE_COUNT;

    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    List<ProcessInstanceForListViewEntity> processInstances =
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
    List<VariableForListViewEntity> variablesList =
        entityReader.searchEntitiesFor(searchRequest, VariableForListViewEntity.class);
    assertThat(variablesList.size()).isEqualTo(processInstancesCount * 4);

    // Activity instances list
    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                termsQuery(ListViewTemplate.PROCESS_INSTANCE_KEY, processInstanceIds)));
    List<FlowNodeInstanceForListViewEntity> activitiesList =
        entityReader.searchEntitiesFor(searchRequest, FlowNodeInstanceForListViewEntity.class);
    assertThat(activitiesList.size()).isEqualTo(processInstancesCount * 3);
  }

  @Test
  public void testIncidents() {
    SearchRequest searchRequest = new SearchRequest(incidentTemplate.getAlias());
    searchRequest
        .source()
        .query(termsQuery(IncidentTemplate.PROCESS_INSTANCE_KEY, processInstanceIds));
    List<IncidentEntity> incidents =
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
    IncidentEntity randomIncident = chooseOne(incidents);
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
    List<PostImporterQueueEntity> postImporterQueueEntities =
        entityReader.getEntitiesFor(
            postImporterQueueTemplate.getAlias(), PostImporterQueueEntity.class);
    long incidentsCount = entityReader.countEntitiesFor(incidentTemplate.getAlias());
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.api.v1.entities.DecisionInstance;
import io.camunda.operate.webapp.api.v1.entities.FlowNodeInstance;
import io.camunda.operate.webapp.api.v1.entities.Incident;
import io.camunda.operate.webapp.api.v1.entities.ProcessInstance;
import io.camunda.operate.webapp.api.v1.entities.Query;
import io.camunda.operate.webapp.api.v1.entities.Results;
import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import io.camunda.webapps.schema.entities.incident.IncidentEntity;
import io.camunda.webapps.schema.entities.incident.IncidentState;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      OperateProperties.PREFIX + ".rfc3339ApiDateFormat = true"
    })
public class DaoRfc3339SerializationIT extends OperateSearchAbstractIT {
  private static final Long FAKE_PROCESS_DEFINITION_KEY = 2251799813685253L;
  private static final Long FAKE_PROCESS_INSTANCE_KEY = 2251799813685255L;
  private final String firstDecisionEvaluationDate = "2024-02-15T22:40:10.834+0000";
  private final String firstDecisionRfc3339EvaluationDate = "2024-02-15T22:40:10.834+00:00";
  private final String secondDecisionEvaluationDate = "2024-02-15T22:41:10.834+0000";
  private final String secondDecisionRfc3339EvaluationDate = "2024-02-15T22:41:10.834+00:00";
  private final String firstNodeStartDate = "2024-02-15T22:40:10.834+0000";
  private final String firstNodeStartDateRfc3339 = "2024-02-15T22:40:10.834+00:00";
  private final String secondNodeStartDate = "2024-02-15T22:41:10.834+0000";
  private final String secondNodeStartDateRfc3339 = "2024-02-15T22:41:10.834+00:00";
  private final String nodeEndDate = "2024-02-15T22:41:10.834+0000";
  private final String nodeEndDateRfc3339 = "2024-02-15T22:41:10.834+00:00";
  private final String firstIncidentCreationTime = "2024-02-15T22:40:10.834+0000";
  private final String firstIncidentRfc3339CreationTime = "2024-02-15T22:40:10.834+00:00";
  private final String secondIncidentCreationTime = "2024-02-15T22:41:10.834+0000";
  private final String secondIncidentRfc3339CreationTime = "2024-02-15T22:41:10.834+00:00";
  private final String firstInstanceStartDate = "2024-02-15T22:40:10.834+0000";
  private final String firstInstanceRfc3339StartDate = "2024-02-15T22:40:10.834+00:00";
  private final String secondInstanceStartDate = "2024-02-15T22:41:10.834+0000";
  private final String secondInstanceRfc3339StartDate = "2024-02-15T22:41:10.834+00:00";
  private final String instanceEndDate = "2024-02-15T22:41:10.834+0000";
  private final String instanceRfc3339endDate = "2024-02-15T22:41:10.834+00:00";

  @Autowired private DecisionInstanceDao decisionInstanceDao;
  @Autowired private FlowNodeInstanceDao flowNodeInstanceDao;
  @Autowired private IncidentDao incidentDao;
  @Autowired private ProcessInstanceDao processInstanceDao;

  @Autowired private DecisionInstanceTemplate decisionInstanceIndex;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Autowired private IncidentTemplate incidentIndex;
  @Autowired private ListViewTemplate processInstanceIndex;

  @Autowired private OperateDateTimeFormatter dateTimeFormatter;

  @MockBean private ProcessCache processCache;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionInstanceIndex.getFullQualifiedName(),
        new DecisionInstanceEntity()
            .setId("2251799813685262-1")
            .setKey(2251799813685262L)
            .setState(DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(firstDecisionEvaluationDate))
            .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY)
            .setDecisionId("invoiceClassification")
            .setDecisionDefinitionId("2251799813685251")
            .setDecisionName("Invoice Classification")
            .setDecisionVersion(1)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult("\"day-to-day expense\"")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    testSearchRepository.createOrUpdateDocumentFromObject(
        decisionInstanceIndex.getFullQualifiedName(),
        new DecisionInstanceEntity()
            .setId("2251799813685262-2")
            .setKey(2251799813685262L)
            .setState(DecisionInstanceState.EVALUATED)
            .setEvaluationDate(dateTimeFormatter.parseGeneralDateTime(secondDecisionEvaluationDate))
            .setProcessDefinitionKey(FAKE_PROCESS_DEFINITION_KEY)
            .setProcessInstanceKey(FAKE_PROCESS_INSTANCE_KEY)
            .setDecisionId("invoiceAssignApprover")
            .setDecisionDefinitionId("2251799813685250")
            .setDecisionName("Assign Approver Group")
            .setDecisionVersion(1)
            .setDecisionType(DecisionType.DECISION_TABLE)
            .setResult("\"day-to-day expense\"")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    testSearchRepository.createOrUpdateDocumentFromObject(
        flowNodeInstanceIndex.getFullQualifiedName(),
        new FlowNodeInstanceEntity()
            .setKey(2251799813685256L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstNodeStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(nodeEndDate))
            .setFlowNodeId("start")
            .setType(FlowNodeType.START_EVENT)
            .setState(FlowNodeState.COMPLETED)
            .setIncident(false)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    testSearchRepository.createOrUpdateDocumentFromObject(
        flowNodeInstanceIndex.getFullQualifiedName(),
        new FlowNodeInstanceEntity()
            .setKey(2251799813685258L)
            .setProcessInstanceKey(2251799813685253L)
            .setProcessDefinitionKey(2251799813685249L)
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondNodeStartDate))
            .setEndDate(null)
            .setFlowNodeId("taskA")
            .setType(FlowNodeType.SERVICE_TASK)
            .setIncidentKey(2251799813685264L)
            .setState(FlowNodeState.ACTIVE)
            .setIncident(true)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483647L)
            .setProcessDefinitionKey(5147483647L)
            .setProcessInstanceKey(6147483647L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Some error")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setCreationTime(dateTimeFormatter.parseGeneralDateTime(firstIncidentCreationTime))
            .setJobKey(2251799813685260L));

    testSearchRepository.createOrUpdateDocumentFromObject(
        incidentIndex.getFullQualifiedName(),
        new IncidentEntity()
            .setKey(7147483648L)
            .setProcessDefinitionKey(5147483648L)
            .setProcessInstanceKey(6147483648L)
            .setErrorType(ErrorType.JOB_NO_RETRIES)
            .setState(IncidentState.ACTIVE)
            .setErrorMessage("Another error")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setCreationTime(dateTimeFormatter.parseGeneralDateTime(secondIncidentCreationTime))
            .setJobKey(3251799813685260L));

    ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-1")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(firstInstanceStartDate))
            .setEndDate(dateTimeFormatter.parseGeneralDateTime(instanceEndDate))
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685251")
            .setIncident(true)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setProcessInstanceKey(2251799813685251L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    processInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(2251799813685252L)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess-2")
            .setStartDate(dateTimeFormatter.parseGeneralDateTime(secondInstanceStartDate))
            .setEndDate(null)
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setIncident(true)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setProcessInstanceKey(2251799813685252L)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        processInstanceIndex.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*operate*");
  }

  @Override
  public void runAdditionalBeforeEachSetup() {
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("start"), eq(null)))
        .thenReturn("start");
    when(processCache.getFlowNodeNameOrDefaultValue(any(), eq("taskA"), eq(null)))
        .thenReturn("task A");
  }

  @Test
  public void shouldFilterDecisionsByEvaluationDate() {
    final Results<DecisionInstance> decisionInstanceResults =
        decisionInstanceDao.search(
            new Query<DecisionInstance>()
                .setFilter(
                    new DecisionInstance().setEvaluationDate(firstDecisionRfc3339EvaluationDate)));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(decisionInstanceResults.getItems().get(0).getEvaluationDate())
        .isEqualTo(firstDecisionRfc3339EvaluationDate);
    assertThat(decisionInstanceResults.getItems().get(0).getId()).isEqualTo("2251799813685262-1");
  }

  @Test
  public void shouldFilterDecisionsByEvaluationDateWithDateMath() {
    final Results<DecisionInstance> decisionInstanceResults =
        decisionInstanceDao.search(
            new Query<DecisionInstance>()
                .setFilter(
                    new DecisionInstance()
                        .setEvaluationDate(firstDecisionRfc3339EvaluationDate + "||/d")));

    assertThat(decisionInstanceResults.getTotal()).isEqualTo(2L);

    DecisionInstance checkDecision =
        decisionInstanceResults.getItems().stream()
            .filter(item -> "2251799813685262-1".equals(item.getId()))
            .findFirst()
            .orElse(null);
    assertThat(checkDecision.getEvaluationDate()).isEqualTo(firstDecisionRfc3339EvaluationDate);
    assertThat(checkDecision.getId()).isEqualTo("2251799813685262-1");

    checkDecision =
        decisionInstanceResults.getItems().stream()
            .filter(item -> "2251799813685262-2".equals(item.getId()))
            .findFirst()
            .orElse(null);
    assertThat(checkDecision.getEvaluationDate()).isEqualTo(secondDecisionRfc3339EvaluationDate);
    assertThat(checkDecision.getId()).isEqualTo("2251799813685262-2");
  }

  @Test
  public void shouldFormatDateWhenSearchDecisionsById() {
    final DecisionInstance decisionInstance = decisionInstanceDao.byId("2251799813685262-1");

    assertThat(decisionInstance.getEvaluationDate()).isEqualTo(firstDecisionRfc3339EvaluationDate);
    assertThat(decisionInstance.getId()).isEqualTo("2251799813685262-1");
  }

  @Test
  public void shouldFilterFlowNodeByStartDate() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        flowNodeInstanceDao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setStartDate(firstNodeStartDateRfc3339)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDateRfc3339);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate())
        .isEqualTo(nodeEndDateRfc3339);
  }

  @Test
  public void shouldFilterFlowNodeByStartDateWithDateMath() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        flowNodeInstanceDao.search(
            new Query<FlowNodeInstance>()
                .setFilter(
                    new FlowNodeInstance().setStartDate(firstNodeStartDateRfc3339 + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(2L);

    FlowNodeInstance checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "START_EVENT".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("start", "start", firstNodeStartDateRfc3339, nodeEndDateRfc3339);

    checkFlowNode =
        flowNodeInstanceResults.getItems().stream()
            .filter(item -> "SERVICE_TASK".equals(item.getType()))
            .findFirst()
            .orElse(null);
    assertThat(checkFlowNode)
        .extracting("flowNodeId", "flowNodeName", "startDate", "endDate")
        .containsExactly("taskA", "task A", secondNodeStartDateRfc3339, null);
  }

  @Test
  public void shouldFilterFlowNodeByEndDate() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        flowNodeInstanceDao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setEndDate(nodeEndDateRfc3339)));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDateRfc3339);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate())
        .isEqualTo(nodeEndDateRfc3339);
  }

  @Test
  public void shouldFilterFlowNodeByEndDateWithDateMath() {
    final Results<FlowNodeInstance> flowNodeInstanceResults =
        flowNodeInstanceDao.search(
            new Query<FlowNodeInstance>()
                .setFilter(new FlowNodeInstance().setEndDate(nodeEndDateRfc3339 + "||/d")));

    assertThat(flowNodeInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(flowNodeInstanceResults.getItems().get(0).getFlowNodeId()).isEqualTo("start");
    assertThat(flowNodeInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstNodeStartDateRfc3339);
    assertThat(flowNodeInstanceResults.getItems().get(0).getEndDate())
        .isEqualTo(nodeEndDateRfc3339);
  }

  @Test
  public void shouldFormatDatesWhenSearchFlowNodeByKey() {
    final FlowNodeInstance flowNodeInstance = flowNodeInstanceDao.byKey(2251799813685256L);

    assertThat(flowNodeInstance.getStartDate()).isEqualTo(firstNodeStartDateRfc3339);
    assertThat(flowNodeInstance.getEndDate()).isEqualTo(nodeEndDateRfc3339);
    assertThat(flowNodeInstance.getKey()).isEqualTo(2251799813685256L);
  }

  @Test
  public void shouldFilterIncidentsByCreationDate() {
    final Results<Incident> incidentResults =
        incidentDao.search(
            new Query<Incident>()
                .setFilter(new Incident().setCreationTime(firstIncidentRfc3339CreationTime)));

    assertThat(incidentResults.getTotal()).isEqualTo(1L);
    assertThat(incidentResults.getItems().get(0).getCreationTime())
        .isEqualTo(firstIncidentRfc3339CreationTime);
    assertThat(incidentResults.getItems().get(0).getMessage()).isEqualTo("Some error");
  }

  @Test
  public void shouldFilterByCreationDateWithDateMath() {
    final Results<Incident> incidentResults =
        incidentDao.search(
            new Query<Incident>()
                .setFilter(
                    new Incident().setCreationTime(firstIncidentRfc3339CreationTime + "||/d")));

    assertThat(incidentResults.getTotal()).isEqualTo(2L);

    Incident checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> "Some error".equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting("creationTime", "message")
        .containsExactly(firstIncidentRfc3339CreationTime, "Some error");

    checkIncident =
        incidentResults.getItems().stream()
            .filter(item -> "Another error".equals(item.getMessage()))
            .findFirst()
            .orElse(null);
    assertThat(checkIncident)
        .extracting("creationTime", "message")
        .containsExactly(secondIncidentRfc3339CreationTime, "Another error");
  }

  @Test
  public void shouldFormatDatesWhenSearchIncidentsByKey() {
    final Incident incident = incidentDao.byKey(7147483647L);

    assertThat(incident.getCreationTime()).isEqualTo(firstIncidentRfc3339CreationTime);
    assertThat(incident.getKey()).isEqualTo(7147483647L);
  }

  @Test
  public void shouldFilterProcessInstanceByStartDate() {
    final Results<ProcessInstance> processInstanceResults =
        processInstanceDao.search(
            new Query<ProcessInstance>()
                .setFilter(new ProcessInstance().setStartDate(firstInstanceRfc3339StartDate)));

    assertThat(processInstanceResults.getTotal()).isEqualTo(1L);
    assertThat(processInstanceResults.getItems().get(0).getStartDate())
        .isEqualTo(firstInstanceRfc3339StartDate);
    assertThat(processInstanceResults.getItems().get(0).getEndDate())
        .isEqualTo(instanceRfc3339endDate);
    assertThat(processInstanceResults.getItems().get(0).getBpmnProcessId())
        .isEqualTo("demoProcess-1");
  }

  @Test
  public void shouldFilterByStartDateWithDateMath() {
    final Results<ProcessInstance> processInstanceResults =
        processInstanceDao.search(
            new Query<ProcessInstance>()
                .setFilter(
                    new ProcessInstance().setStartDate(firstInstanceRfc3339StartDate + "||/d")));

    assertThat(processInstanceResults.getTotal()).isEqualTo(2L);

    ProcessInstance checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-1".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-1");
    assertThat(checkInstance.getStartDate()).isEqualTo(firstInstanceRfc3339StartDate);
    assertThat(checkInstance.getEndDate()).isEqualTo(instanceRfc3339endDate);

    checkInstance =
        processInstanceResults.getItems().stream()
            .filter(item -> "demoProcess-2".equals(item.getBpmnProcessId()))
            .findFirst()
            .orElse(null);

    assertThat(checkInstance.getBpmnProcessId()).isEqualTo("demoProcess-2");
    assertThat(checkInstance.getStartDate()).isEqualTo(secondInstanceRfc3339StartDate);
    assertThat(checkInstance.getEndDate()).isNull();
  }

  @Test
  public void shouldFormatDatesWhenSearchProcessInstanceByKey() {
    final ProcessInstance processInstance = processInstanceDao.byKey(2251799813685251L);

    assertThat(processInstance.getStartDate()).isEqualTo(firstInstanceRfc3339StartDate);
    assertThat(processInstance.getEndDate()).isEqualTo(instanceRfc3339endDate);
    assertThat(processInstance.getKey()).isEqualTo(2251799813685251L);
  }
}

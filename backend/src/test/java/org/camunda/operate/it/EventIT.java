/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.es.reader.ActivityInstanceReader;
import org.camunda.operate.es.reader.EventReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.assertj.core.api.Assertions.assertThat;

public class EventIT extends OperateZeebeIntegrationTest {

  @Autowired
  private EventReader eventReader;

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  private Predicate<Object[]> workflowInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  private Predicate<Object[]> workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("incidentIsResolvedCheck")
  private Predicate<Object[]> incidentIsResolvedCheck;

  @Autowired
  @Qualifier("activityIsTerminatedCheck")
  private Predicate<Object[]> activityIsTerminatedCheck;

  @Autowired
  private ActivityInstanceReader activityInstanceReader;

  @Autowired
  private IncidentReader incidentReader;

  private OffsetDateTime testStartTime;

  private ZeebeClient zeebeClient;

  @Before
  public void init() {
    super.before();
    testStartTime = OffsetDateTime.now();
    zeebeClient = super.getClient();
  }

  @Test
  public void testEventsForFinishedWorkflow() {
    // having
    final String processId = "processWithGateway";
    final String taskA = "taskA";
    final String taskC = "taskC";
    final String errorMessage = "Some error";
    final String workflowId = deployWorkflow("processWithGateway.bpmn");

    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = failTaskWithNoRetriesLeft(taskA, workflowInstanceKey, errorMessage);

    //update retries
    final String workflowInstanceId = IdTestUtil.getId(workflowInstanceKey);
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidents(workflowInstanceId);
    assertThat(allIncidents).hasSize(1);
    ZeebeTestUtil.resolveIncident(zeebeClient, allIncidents.get(0).getJobId(), allIncidents.get(0).getKey());
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsResolvedCheck, workflowInstanceKey);

    //complete task A
    completeTask(workflowInstanceKey, taskA, "{\"goToTaskC\":true}");

    //complete task C
    completeTask(workflowInstanceKey, taskC, "{\"b\": \"d\"}");

    elasticsearchTestRule.processAllRecordsAndWait(activityIsCompletedCheck, workflowInstanceKey, taskC);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(IdTestUtil.getId(workflowInstanceKey));
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    assertThat(eventEntities).isSortedAccordingTo((e1, e2) -> {
      final int compareDateTime = e1.getDateTime().compareTo(e2.getDateTime());
      if (compareDateTime == 0) {
        return e1.getId().compareTo(e2.getId());
      } else {
        return compareDateTime;
      }
    });

    //then
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, "start");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATING, 1, processId, workflowId, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 4, processId, workflowId, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.FAILED, 3, processId, workflowId, workflowInstanceKey, "taskA");
    //INCIDENT events do not have workflowId for some reason
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.CREATED, 1, processId, null, workflowInstanceKey, "taskA", errorMessage);
    assertEvent(eventEntities, EventSourceType.JOB, EventType.RETRIES_UPDATED, 1, processId, workflowId, workflowInstanceKey, "taskA");
    //INCIDENT events do not have workflowId for some reason
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.RESOLVED, 1, processId, null, workflowInstanceKey, "taskA", errorMessage);

    assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceKey, "taskA");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETING, 1, processId, workflowId, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATING, 1, processId, workflowId, workflowInstanceKey, "gateway");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, "gateway");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETING, 1, processId, workflowId, workflowInstanceKey, "gateway");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, "gateway");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATING, 1, processId, workflowId, workflowInstanceKey, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, "taskC");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceKey, "taskC");

    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 1, processId, workflowId, workflowInstanceKey, "taskC");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceKey, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETING, 1, processId, workflowId, workflowInstanceKey, "taskC");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, "end1");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, "processWithGateway");

  }

  @Test
  public void testWorkflowInstanceCanceledOnIncident() {
    // having
    String activityId = "taskA";

    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    cancelWorkflowInstance(workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsTerminatedCheck, workflowInstanceKey, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(IdTestUtil.getId(workflowInstanceKey));
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    //then
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.RESOLVED, 1, processId, null, workflowInstanceKey, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, workflowId, workflowInstanceKey, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, workflowId, workflowInstanceKey, processId);

  }

  @Test
  public void testIncidentOnInputMapping() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask("task1").zeebeTaskType("task1")
      .zeebeInput("var", "varIn")
      .endEvent()
      .done();

    deployWorkflow(workflow, processId + ".bpmn");

    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(IdTestUtil.getId(workflowInstanceKey));
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    //then last event does not have a jobId
    final EventEntity lastEvent = eventEntities.get(eventEntities.size() - 1);
    assertThat(lastEvent.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
    assertThat(lastEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(lastEvent.getMetadata().getJobId()).isNull();

  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, long workflowInstanceKey) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowId, workflowInstanceKey, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, long workflowInstanceKey, String activityId) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowId, workflowInstanceKey, activityId, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, long workflowInstanceKey, String activityId, String errorMessage) {
    String assertionName = String.format("%s.%s", eventSourceType, eventType);
    final Predicate<EventEntity> eventEntityFilterCriteria = eventEntity -> {
      boolean b = true;
      if (activityId != null) {
        b = eventEntity.getActivityId().equals(activityId);
      } else {
        b = (eventEntity.getActivityId() == null) || eventEntity.getActivityId().isEmpty();
      }
      return b && eventEntity.getEventSourceType().equals(eventSourceType) && eventEntity.getEventType().equals(eventType);
    };
    assertThat(eventEntities)
      .filteredOn(eventEntityFilterCriteria).as(assertionName + ".size").hasSize(count);
    eventEntities.stream().filter(eventEntityFilterCriteria)
      .forEach(eventEntity -> {
        assertThat(eventEntity.getWorkflowInstanceId()).as(assertionName + ".workflowInstanceId").isEqualTo(IdTestUtil.getId(workflowInstanceKey));
        if (workflowId != null) {
          assertThat(eventEntity.getWorkflowId()).as(assertionName + ".workflowId").isEqualTo(workflowId);
        }
        assertThat(eventEntity.getDateTime()).as(assertionName + ".dateTimeAfter").isAfterOrEqualTo(testStartTime);
        assertThat(eventEntity.getDateTime()).as(assertionName + ".dateTimeBefore").isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(eventEntity.getBpmnProcessId()).as(assertionName + ".bpmnProcessId").isEqualTo(processId);
        if (activityId != null) {
          assertThat(eventEntity.getActivityId()).as(assertionName + ".activityId").isEqualTo(activityId);
          if (eventEntity.getKey() != IdUtil.getKey(eventEntity.getWorkflowInstanceId())) {
            assertThat(eventEntity.getActivityInstanceId()).as(assertionName + ".activityInstanceId").isNotNull();
          }
        }
        if (eventSourceType.equals(EventSourceType.INCIDENT)) {
          if (errorMessage != null) {
            assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isEqualTo(errorMessage);
          } else {
            assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isNotEmpty();
          }
          assertThat(eventEntity.getMetadata().getIncidentErrorType()).as(assertionName + ".incidentErrorType").isNotEmpty();
        }
      });
  }

}
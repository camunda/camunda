/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.webapp.es.reader.EventReader;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.rest.dto.EventQueryDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.ZeebeTestUtil;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

@Deprecated
public class EventIT extends OperateZeebeIntegrationTest {

  @Autowired
  private EventReader eventReader;

  @Autowired
  private IncidentReader incidentReader;

  @Test
  public void testEventsForFinishedWorkflow() {
    // having
    final String processId = "processWithGateway";
    final String taskA = "taskA";
    final String taskC = "taskC";
    final String errorMessage = "Some error";
    final Long workflowKey = deployWorkflow("processWithGateway.bpmn");

    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, "{\"a\": \"b\"}");

    //create an incident
    /*final Long jobKey =*/ failTaskWithNoRetriesLeft(taskA, workflowInstanceKey, errorMessage);

    //update retries
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    ZeebeTestUtil.resolveIncident(zeebeClient, allIncidents.get(0).getJobKey(), allIncidents.get(0).getKey());
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
    eventQueryDto.setWorkflowInstanceId(workflowInstanceKey.toString());
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
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "start");
    try {
      assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "taskA");
    } catch (AssertionError ae) {
      assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "taskA");
    }
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "gateway");
    try {
      assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "taskC");
    } catch (AssertionError ae) {
      assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "taskC");
    }
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "end1");

  }

  @Test
  public void testWorkflowInstanceCanceledOnIncident() {
    // having
    String activityId = "taskA";

    String processId = "demoProcess";
    final Long workflowKey = deployWorkflow("demoProcess_v_1.bpmn");
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, null);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    cancelWorkflowInstance(workflowInstanceKey);
    elasticsearchTestRule.processAllRecordsAndWait(activityIsTerminatedCheck, workflowInstanceKey, activityId);
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceKey.toString());
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    //then
    try {
      assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, workflowKey, workflowInstanceKey, activityId);
    } catch (AssertionError ae) {
      assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.RESOLVED, 1, processId, null, workflowInstanceKey, activityId);
    }

  }

  @Test
  public void testIncidentOnInputMapping() {
    // having
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask("task1").zeebeJobType("task1")
      .zeebeInput("=var", "varIn")
      .endEvent()
      .done();

    deployWorkflow(workflow, processId + ".bpmn");

    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");      //wrong payload provokes incident
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceKey.toString());
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    //then last event does not have a jobId
    final EventEntity lastEvent = eventEntities.get(eventEntities.size() - 1);
    try {
      assertThat(lastEvent.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
      assertThat(lastEvent.getEventType()).isEqualTo(EventType.CREATED);
      assertThat(lastEvent.getMetadata().getJobKey()).isNull();
    } catch (AssertionError ae) {
      assertThat(lastEvent.getEventSourceType()).isEqualTo(EventSourceType.WORKFLOW_INSTANCE);
      assertThat(lastEvent.getEventType()).isEqualTo(EventType.ELEMENT_ACTIVATING);
    }

  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, Long workflowKey, long workflowInstanceKey) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowKey, workflowInstanceKey, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, Long workflowKey, Long workflowInstanceKey, String activityId) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowKey, workflowInstanceKey, activityId, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, Long workflowKey, Long workflowInstanceKey, String activityId, String errorMessage) {
    String assertionName = String.format("%s.%s", eventSourceType, eventType);
    final Predicate<EventEntity> eventEntityFilterCriteria = eventEntity -> {
      boolean b = true;
      if (activityId != null) {
        b = eventEntity.getFlowNodeId().equals(activityId);
      } else {
        b = (eventEntity.getFlowNodeId() == null) || eventEntity.getFlowNodeId().isEmpty();
      }
      return b && eventEntity.getEventSourceType().equals(eventSourceType) && eventEntity.getEventType().equals(eventType);
    };
    assertThat(eventEntities)
      .filteredOn(eventEntityFilterCriteria).as(assertionName + ".size").hasSize(count);
    eventEntities.stream().filter(eventEntityFilterCriteria)
      .forEach(eventEntity -> {
        assertThat(eventEntity.getWorkflowInstanceKey()).as(assertionName + ".workflowInstanceKey").isEqualTo(workflowInstanceKey);
        if (workflowKey != null) {
          assertThat(eventEntity.getWorkflowKey()).as(assertionName + ".workflowKey").isEqualTo(workflowKey);
        }
        assertThat(eventEntity.getDateTime()).as(assertionName + ".dateTimeAfter").isAfterOrEqualTo(testStartTime);
        assertThat(eventEntity.getDateTime()).as(assertionName + ".dateTimeBefore").isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(eventEntity.getBpmnProcessId()).as(assertionName + ".bpmnProcessId").isEqualTo(processId);
        if (activityId != null) {
          assertThat(eventEntity.getFlowNodeId()).as(assertionName + ".activityId").isEqualTo(activityId);
          if (eventEntity.getKey() != eventEntity.getWorkflowInstanceKey()) {
            assertThat(eventEntity.getFlowNodeInstanceKey()).as(assertionName + ".flowNodeInstanceKey").isNotNull();
          }
        }
        if (eventSourceType.equals(EventSourceType.INCIDENT)) {
          if (errorMessage != null) {
            assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isEqualTo(errorMessage);
          } else {
            assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isNotEmpty();
          }
          assertThat(eventEntity.getMetadata().getIncidentErrorType()).as(assertionName + ".incidentErrorType").isNotNull();
        }
      });
  }

}

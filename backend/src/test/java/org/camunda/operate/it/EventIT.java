/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.es.reader.EventReader;
import org.camunda.operate.es.reader.IncidentReader;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

public class EventIT extends OperateZeebeIntegrationTest {

  @Autowired
  private EventReader eventReader;

  @Autowired
  private IncidentReader incidentReader;
  
  @Autowired
  OperateTester tester;
  
  @Before
  public void before() {
    super.before();
    tester.setZeebeClient(getClient());
  }

  @Test
  public void testEventsForFinishedWorkflow() {
    // given
    final String processId = "processWithGateway";
    tester
      .deployWorkflow("processWithGateway.bpmn")
      .startWorkflowInstance(processId,"{\"a\": \"b\"}")
      .failTask("taskA", "some error").waitUntil().incidentIsActive();
    
    Long workflowKey = tester.getWorkflowKey();
    Long workflowInstanceKey = tester.getWorkflowInstanceKey();
     
    List<IncidentEntity> allIncidents = incidentReader.getAllIncidentsByWorkflowInstanceKey(workflowInstanceKey);
    assertThat(allIncidents).hasSize(1);
    Long jobKey = allIncidents.get(0).getJobKey();
    Long incidentKey = allIncidents.get(0).getKey();
    
    tester
      .resolveIncident(jobKey,incidentKey).waitUntil().incidentIsResolved()
      .completeTask("taskA","{\"goToTaskC\":true}")
      .completeTask("taskC", "{\"b\": \"d\"}").waitUntil().activityIsCompleted("taskC")
      .and()
      .workflowInstanceIsCompleted();
 
    // when
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
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowKey, workflowInstanceKey, "taskA");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "gateway");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "end1");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowKey, workflowInstanceKey, "processWithGateway");
  }

  @Test
  public void testWorkflowInstanceCanceledOnIncident() {
    // given
    String processId = "demoProcess";
    String activityId = "taskA";
    tester
      .deployWorkflow("demoProcess_v_1.bpmn")
      .startWorkflowInstance(processId)
      .waitUntil().incidentIsActive()
      .and()
      .cancelWorkflowInstance().waitUntil().workflowInstanceIsCanceled()
      .and()
      .waitUntil().activityIsTerminated(activityId);
          
    Long workflowKey = tester.getWorkflowKey();
    Long workflowInstanceKey = tester.getWorkflowInstanceKey();
  
    // when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceKey.toString());
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    // then
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.RESOLVED, 1, processId, null, workflowInstanceKey, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, workflowKey, workflowInstanceKey, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, workflowKey, workflowInstanceKey, processId);
  }

  @Test
  public void testIncidentOnInputMapping() {
    // given
    String wrongPayload = "{\"a\": \"b\"}";
    String processId = "demoProcess";
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask("task1").zeebeTaskType("task1")
      .zeebeInput("var", "varIn")
      .endEvent()
      .done();
    
    Long workflowInstanceKey = tester
      .deployWorkflow(workflow,processId+".bpmn")
      .startWorkflowInstance(processId,wrongPayload)
      .waitUntil().incidentIsActive()
      .and()
      .getWorkflowInstanceKey();
      
    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceKey.toString());
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto);

    //then last event does not have a jobId
    final EventEntity lastEvent = eventEntities.get(eventEntities.size() - 1);
    assertThat(lastEvent.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
    assertThat(lastEvent.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(lastEvent.getMetadata().getJobKey()).isNull();
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, Long workflowKey, long workflowInstanceKey) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowKey, workflowInstanceKey, null);
  }

  protected void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, Long workflowKey, Long workflowInstanceKey, String activityId) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowKey, workflowInstanceKey, activityId, null);
  }

  protected void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, Long workflowKey, Long workflowInstanceKey, String activityId, String errorMessage) {
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
        assertThat(eventEntity.getWorkflowInstanceKey()).as(assertionName + ".workflowInstanceKey").isEqualTo(workflowInstanceKey);
        if (workflowKey != null) {
          assertThat(eventEntity.getWorkflowKey()).as(assertionName + ".workflowKey").isEqualTo(workflowKey);
        }
        assertThat(eventEntity.getDateTime()).as(assertionName + ".dateTimeAfter").isAfterOrEqualTo(testStartTime);
        assertThat(eventEntity.getDateTime()).as(assertionName + ".dateTimeBefore").isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(eventEntity.getBpmnProcessId()).as(assertionName + ".bpmnProcessId").isEqualTo(processId);
        if (activityId != null) {
          assertThat(eventEntity.getActivityId()).as(assertionName + ".activityId").isEqualTo(activityId);
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
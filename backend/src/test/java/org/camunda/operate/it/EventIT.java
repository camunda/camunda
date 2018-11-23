package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.es.reader.EventReader;
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
  @Qualifier("activityIsCompletedCheck")
  private Predicate<Object[]> activityIsCompletedCheck;

  @Autowired
  @Qualifier("activityIsTerminatedCheck")
  private Predicate<Object[]> activityIsTerminatedCheck;

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    super.before();
    testStartTime = OffsetDateTime.now();
  }

  @Test
  public void testEventsForFinishedWorkflow() {
    // having
    String processId = "processWithGateway";
    String taskA = "taskA";
    String taskC = "taskC";
    final String workflowId = deployWorkflow("processWithGateway.bpmn");

    final String initialPayload = "{\"a\": \"b\"}";
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, initialPayload);

    //create an incident
    final Long jobKey = failTaskWithNoRetriesLeft(taskA, workflowInstanceKey);

    //update retries to delete the incident
    ZeebeTestUtil.resolveIncident(super.getClient(), jobKey);
    elasticsearchTestRule.processAllEvents(10);

    //complete task A
    String taskAPayload = "{\"goToTaskC\":true}";
    completeTask(workflowInstanceKey, taskA, taskAPayload);

    //update process payload
//    final String updatedPayload = "{\"a\": \"c\"}";
//    ZeebeUtil.updatePayload(super.getClient(), IdUtil.extractKey(workflowInstanceId), workflowInstanceId, updatedPayload, processId, workflowId);
//    elasticsearchTestRule.processAllEvents(5);

    //complete task C
    final String taskCPayload = "{\"b\": \"d\"}";
    completeTask(workflowInstanceKey, taskC, taskCPayload);

    elasticsearchTestRule.processAllEventsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);
    elasticsearchTestRule.processAllEventsAndWait(activityIsCompletedCheck, workflowInstanceKey, taskC);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(IdTestUtil.getId(workflowInstanceKey));
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto, 0, 1000);

    assertThat(eventEntities).isSortedAccordingTo((e1, e2) -> {
      final int compareDateTime = e1.getDateTime().compareTo(e2.getDateTime());
      if (compareDateTime == 0) {
        return e1.getId().compareTo(e2.getId());
      } else {
        return compareDateTime;
      }
    });

    //then
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.START_EVENT_OCCURRED, 1, processId, workflowId, workflowInstanceKey, initialPayload, "start");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_READY, 1, processId, workflowId, workflowInstanceKey, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceKey, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 4, processId, workflowId, workflowInstanceKey, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.FAILED, 3, processId, workflowId, workflowInstanceKey, initialPayload, "taskA");
    //INCIDENT events do not have workflowId for some reason
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.CREATED, 1, processId, null, workflowInstanceKey, null, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.RETRIES_UPDATED, 1, processId, workflowId, workflowInstanceKey, initialPayload, "taskA");
    //INCIDENT events do not have workflowId for some reason
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.DELETED, 1, processId, null, workflowInstanceKey, null, "taskA");

    assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceKey, taskAPayload, "taskA");
    String afterTaskAJoinedPayload = "{\"a\":\"b\",\"goToTaskC\":true}";
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETING, 1, processId, workflowId, workflowInstanceKey, taskAPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, afterTaskAJoinedPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.GATEWAY_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, afterTaskAJoinedPayload, "gateway");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_READY, 1, processId, workflowId, workflowInstanceKey, afterTaskAJoinedPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_ACTIVATED, 1, processId, workflowId, workflowInstanceKey, afterTaskAJoinedPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceKey, afterTaskAJoinedPayload, "taskC");

//    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.PAYLOAD_UPDATED, 1, processId, workflowId, workflowInstanceId, updatedPayload, null);

    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 1, processId, workflowId, workflowInstanceKey, afterTaskAJoinedPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceKey, taskCPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETING, 1, processId, workflowId, workflowInstanceKey, taskCPayload, "taskC");

    String finalPayload = "{\"a\":\"b\",\"goToTaskC\":true,\"b\": \"d\"}";
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, finalPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.END_EVENT_OCCURRED, 1, processId, workflowId, workflowInstanceKey, finalPayload, "end1");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_COMPLETED, 1, processId, workflowId, workflowInstanceKey, finalPayload, "processWithGateway");

  }

  @Test
  public void testWorkflowInstanceCanceled() {
    // having
    String activityId = "taskA";

    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(super.getClient(), processId, "{\"a\": \"b\"}");
    super.setJobWorker(ZeebeTestUtil.completeTask(super.getClient(), activityId, super.getWorkerName(), "{\"a\": \"b\"}"));

    cancelWorkflowInstance(workflowInstanceKey);
    elasticsearchTestRule.processAllEventsAndWait(activityIsTerminatedCheck, workflowInstanceKey, activityId);
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(IdTestUtil.getId(workflowInstanceKey));
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto, 0, 1000);

    //then
    //ACTIVITY_TERMINATED has workflowKey = -1 for some reason -> not asserting
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, null, workflowInstanceKey, null, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ELEMENT_TERMINATED, 1, processId, workflowId, workflowInstanceKey, null, "demoProcess");

  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, long workflowInstanceKey, String payload) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowId, workflowInstanceKey, payload, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, long workflowInstanceKey, String payload, String activityId) {
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
        if (payload != null) {
          assertThat(eventEntity.getPayload()).as(assertionName + ".payload").isEqualToIgnoringWhitespace(payload);
        }
        if (activityId != null) {
          assertThat(eventEntity.getActivityId()).as(assertionName + ".activityId").isEqualTo(activityId);
          if (eventEntity.getKey() != IdUtil.getKey(eventEntity.getWorkflowInstanceId())) {
            assertThat(eventEntity.getActivityInstanceId()).as(assertionName + ".activityInstanceId").isNotNull();
          }
        }
        if (eventSourceType.equals(EventSourceType.INCIDENT)) {
          assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isNotEmpty();
          assertThat(eventEntity.getMetadata().getIncidentErrorType()).as(assertionName + ".incidentErrorType").isNotEmpty();
        }
      });
  }

}
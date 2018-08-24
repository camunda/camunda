package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Predicate;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.EventSourceType;
import org.camunda.operate.entities.EventType;
import org.camunda.operate.es.reader.EventReader;
import org.camunda.operate.rest.dto.EventQueryDto;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.ZeebeTestRule;
import org.camunda.operate.util.ZeebeUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.api.subscription.TopicSubscription;
import static org.assertj.core.api.Assertions.assertThat;

public class EventIT extends OperateIntegrationTest {

  @Rule
  public ZeebeTestRule zeebeTestRule = new ZeebeTestRule();

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  private ZeebeUtil zeebeUtil;

  @Autowired
  private EventReader eventReader;

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    testStartTime = OffsetDateTime.now();
  }

  @Test
  public void testEventsForFinishedWorkflow() {
    // having
    String topicName = zeebeTestRule.getTopicName();

    String processId = "processWithGateway";
    String taskA = "taskA";
    String taskC = "taskC";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "processWithGateway.bpmn");

    final String initialPayload = "{\"a\": \"b\"}";
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, initialPayload);

    //create an incident
    zeebeTestRule.setJobWorker(zeebeUtil.failTask(topicName, taskA, zeebeTestRule.getWorkerName(), 3));
    elasticsearchTestRule.processAllEvents(20);
    zeebeTestRule.getJobWorker().close();
    zeebeTestRule.setJobWorker(null);

    //update retries to delete the incident
    final TopicSubscription topicSubscription = zeebeUtil.resolveIncident(topicName, "testEventsForFinishedWorkflow", workflowId, initialPayload);
    elasticsearchTestRule.processAllEvents(10);
    topicSubscription.close();

    //complete task A
    String taskAPayload = "{\"goToTaskC\":true}";
    zeebeTestRule.setJobWorker(zeebeUtil.completeTask(topicName, taskA, zeebeTestRule.getWorkerName(), taskAPayload));
    elasticsearchTestRule.processAllEvents(13);
    zeebeTestRule.getJobWorker().close();
    zeebeTestRule.setJobWorker(null);

    //update process payload
    final String updatedPayload = "{\"a\": \"c\"}";
    zeebeUtil.updatePayload(topicName, workflowInstanceId, workflowInstanceId, updatedPayload, processId, workflowId);
    elasticsearchTestRule.processAllEvents(5);

    //complete task C
    final String taskCPayload = "{\"b\": \"d\"}";
    zeebeTestRule.setJobWorker(zeebeUtil.completeTask(topicName, taskC, zeebeTestRule.getWorkerName(), taskCPayload));
    elasticsearchTestRule.processAllEvents(10);
    zeebeTestRule.getJobWorker().close();
    zeebeTestRule.setJobWorker(null);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceId);
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto, 0, 1000);

    //then
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.CREATED, 1, processId, workflowId, workflowInstanceId, initialPayload);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.START_EVENT_OCCURRED, 1, processId, workflowId, workflowInstanceId, initialPayload);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_READY, 1, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_ACTIVATED, 1, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 4, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.FAILED, 3, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    //INCIDENT events do not have workflowId for some reason
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.CREATED, 1, processId, null, workflowInstanceId, null, "taskA");
    //JOB RETRIES_UPDATED comes 2 times for some reason
    assertEvent(eventEntities, EventSourceType.JOB, EventType.RETRIES_UPDATED, 2, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    //INCIDENT events do not have workflowId for some reason
    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.DELETED, 1, processId, null, workflowInstanceId, null, "taskA");

    assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceId, taskAPayload, "taskA");
    String afterTaskAJoinedPayload = "{\"a\":\"b\",\"goToTaskC\":true}";
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_COMPLETING, 1, processId, workflowId, workflowInstanceId, taskAPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_COMPLETED, 1, processId, workflowId, workflowInstanceId, afterTaskAJoinedPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.GATEWAY_ACTIVATED, 1, processId, workflowId, workflowInstanceId, afterTaskAJoinedPayload, "gateway");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_READY, 1, processId, workflowId, workflowInstanceId, afterTaskAJoinedPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_ACTIVATED, 1, processId, workflowId, workflowInstanceId, afterTaskAJoinedPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceId, afterTaskAJoinedPayload, "taskC");

    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.PAYLOAD_UPDATED, 1, processId, workflowId, workflowInstanceId, updatedPayload, null);

    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 1, processId, workflowId, workflowInstanceId, afterTaskAJoinedPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceId, taskCPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_COMPLETING, 1, processId, workflowId, workflowInstanceId, taskCPayload, "taskC");

    String finalPayload = "{\"a\":\"c\", \"b\": \"d\"}";
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_COMPLETED, 1, processId, workflowId, workflowInstanceId, finalPayload, "taskC");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.END_EVENT_OCCURRED, 1, processId, workflowId, workflowInstanceId, finalPayload, "end1");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.COMPLETED, 1, processId, workflowId, workflowInstanceId, finalPayload);

  }

  @Test
  public void testWorkflowInstanceCanceled() {
    // having
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "taskA";

    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");
    zeebeTestRule.setJobWorker(zeebeUtil.completeTask(topicName, activityId, zeebeTestRule.getWorkerName(), "{\"a\": \"b\"}"));

    zeebeTestRule.getTopicSubscriptions().add(zeebeUtil.cancelWorkflowInstance(topicName, workflowInstanceId, workflowId));
    elasticsearchTestRule.processAllEvents(20);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceId);
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto, 0, 1000);

    //then
    //ACTIVITY_TERMINATED has workflowKey = -1 for some reason -> not asserting
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_TERMINATED, 1, processId, null, workflowInstanceId, null, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.CANCELED, 1, processId, workflowId, workflowInstanceId, null);

  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, String workflowInstanceId, String payload) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowId, workflowInstanceId, payload, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, String workflowInstanceId, String payload, String activityId) {
    String assertionName = String.format("%s.%s", eventSourceType, eventType);
    final Predicate<EventEntity> eventEntityFilterCriteria = eventEntity -> {
      boolean b = true;
      if (activityId != null) {
        b = eventEntity.getActivityId().equals(activityId);
      }
      return b && eventEntity.getEventSourceType().equals(eventSourceType) && eventEntity.getEventType().equals(eventType);
    };
    assertThat(eventEntities)
      .filteredOn(eventEntityFilterCriteria).as(assertionName + ".size").hasSize(count);
    eventEntities.stream().filter(eventEntityFilterCriteria)
      .forEach(eventEntity -> {
        assertThat(eventEntity.getWorkflowInstanceId()).as(assertionName + ".workflowInstanceId").isEqualTo(workflowInstanceId);
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
          assertThat(eventEntity.getActivityInstanceId()).as(assertionName + ".activityInstanceId").isNotNull();
        }
        if (eventSourceType.equals(EventSourceType.INCIDENT)) {
          assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isNotEmpty();
          assertThat(eventEntity.getMetadata().getIncidentErrorType()).as(assertionName + ".incidentErrorType").isNotEmpty();
        }
      });
  }

}
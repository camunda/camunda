package org.camunda.operate.it;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.api.subscription.JobWorker;
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

  private JobWorker jobWorker;

  private List<TopicSubscription> topicSubscriptions = new ArrayList<>();

  private OffsetDateTime testStartTime;

  @Before
  public void init() {
    testStartTime = OffsetDateTime.now();
  }

  @After
  public void cleanup() {

    for (Iterator<TopicSubscription> iterator = topicSubscriptions.iterator(); iterator.hasNext(); ) {
      iterator.next().close();
      iterator.remove();
    }

    if (jobWorker != null && jobWorker.isOpen()) {
      jobWorker.close();
      jobWorker = null;
    }
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
    jobWorker = zeebeUtil.failTask(topicName, taskA, zeebeTestRule.getWorkerName(), 3);
    elasticsearchTestRule.processAllEvents(10);
    jobWorker.close();
    jobWorker = null;

    //update retries to delete the incident
    final TopicSubscription topicSubscription = zeebeUtil.resolveIncident(topicName, "testEventsForFinishedWorkflow", Long.valueOf(workflowId));
    elasticsearchTestRule.processAllEvents(10);
    topicSubscription.close();

    //complete task A
    jobWorker = zeebeUtil.completeTask(topicName, taskA, zeebeTestRule.getWorkerName(), "{\"goToTaskC\": true}");
    elasticsearchTestRule.processAllEvents(10);
    jobWorker.close();
    jobWorker = null;

    //update process payload  -- TODO this is not working, as the gateway is not passed
    zeebeUtil.updatePayload(topicName, workflowInstanceId, "{\"goToTaskC\": true, \"var\": \"b\"}");
    elasticsearchTestRule.processAllEvents(10);

    //complete task B -- TODO this is not working, as the gateway is not passed
    jobWorker = zeebeUtil.completeTask(topicName, taskC, zeebeTestRule.getWorkerName(), "{\"goToTaskC\": true, \"var\": \"b\"}");
    elasticsearchTestRule.processAllEvents(10);
    jobWorker.close();
    jobWorker = null;

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceId);
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto, 0, 1000);

    //then
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.CREATED, 1, processId, workflowId, workflowInstanceId, initialPayload);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.START_EVENT_OCCURRED, 1, processId, workflowId, workflowInstanceId, initialPayload);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_READY, 1, processId, workflowId, workflowInstanceId, initialPayload, "taskA");
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_ACTIVATED, 1, processId, workflowId, workflowInstanceId, "{\"foo\":\"b\"}", "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.CREATED, 1, processId, workflowId, workflowInstanceId, "{\"foo\":\"b\"}", "taskA");
//TODO    assertEvent(eventEntities, EventSourceType.JOB, EventType.ACTIVATED, 4, processId, workflowId, workflowInstanceId, "{\"foo\":\"b\"}", "taskA");
    assertEvent(eventEntities, EventSourceType.JOB, EventType.FAILED, 3, processId, workflowId, workflowInstanceId, "{\"foo\":\"b\"}", "taskA");
//TODO    assertEvent(eventEntities, EventSourceType.INCIDENT, EventType.CREATED, 1, processId, workflowId, workflowInstanceId, "{\"foo\":\"b\"}", "taskA");
// TODO further assertions
  }

  @Test
//  @Ignore
  public void testWorkflowInstanceCanceled() {
    // having
    String topicName = zeebeTestRule.getTopicName();
    String activityId = "taskA";

    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");
    jobWorker = zeebeUtil.completeTask(topicName, activityId, zeebeTestRule.getWorkerName(), "{\"a\": \"b\"}");

    topicSubscriptions.add(zeebeUtil.cancelWorkflowInstance(topicName, workflowInstanceId));
    elasticsearchTestRule.processAllEvents(20);

    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    EventQueryDto eventQueryDto = new EventQueryDto();
    eventQueryDto.setWorkflowInstanceId(workflowInstanceId);
    final List<EventEntity> eventEntities = eventReader.queryEvents(eventQueryDto, 0, 1000);

    //then
//TODO    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.ACTIVITY_TERMINATED, 1, processId, workflowId, workflowInstanceId, null, activityId);
    assertEvent(eventEntities, EventSourceType.WORKFLOW_INSTANCE, EventType.CANCELED, 1, processId, workflowId, workflowInstanceId, null);

  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, String workflowInstanceId, String payload) {
    assertEvent(eventEntities, eventSourceType, eventType, count, processId, workflowId, workflowInstanceId, payload, null);
  }

  public void assertEvent(List<EventEntity> eventEntities, EventSourceType eventSourceType, EventType eventType,
    int count, String processId, String workflowId, String workflowInstanceId, String payload, String activityId) {
    String assertionName = String.format("%s.%s", eventSourceType, eventType);
    final Predicate<EventEntity> eventEntityFilterCritetia = eventEntity -> {
      boolean b = true;
      if (activityId != null) {
        b = eventEntity.getActivityId().equals(activityId);
      }
      return b && eventEntity.getEventSourceType().equals(eventSourceType) && eventEntity.getEventType().equals(eventType);
    };
    assertThat(eventEntities)
      .filteredOn(eventEntityFilterCritetia).as(assertionName + ".size").hasSize(count);
    eventEntities.stream().filter(eventEntityFilterCritetia)
      .forEach(eventEntity -> {
        assertThat(eventEntity.getWorkflowInstanceId()).as(assertionName + ".workflowInstanceId").isEqualTo(workflowInstanceId);
        assertThat(eventEntity.getWorkflowId()).as(assertionName + ".workflowId").isEqualTo(workflowId);
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
          assertThat(eventEntity.getIncidentErrorMessage()).as(assertionName + ".incidentErrorMessage").isNotEmpty();
          assertThat(eventEntity.getIncidentErrorType()).as(assertionName + ".incidentErrorType").isNotEmpty();
        }
      });
  }

}
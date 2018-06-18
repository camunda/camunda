package org.camunda.operate.zeebe;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.util.ZeebeIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Svetlana Dorokhova.
 */
public class ZeebeWorkflowInstanceIT extends ZeebeIntegrationTest {

  @Before
  public void before() {
    super.starting();
  }

  @After
  public void after() {
    super.finished();
  }

  @Test
  public void testWorkflowInstanceCreated() {
    // having
    final OffsetDateTime testStartTime = OffsetDateTime.now();
    final String topicName = getNewTopicName();

    String processId = "demoProcess";
    final String workflowId = zeebeUtil.deployWorkflowToTheTopic(topicName, "demoProcess_v_1.bpmn");

    //when
    final String workflowInstanceId = zeebeUtil.startWorkflowInstance(topicName, processId, "{\"a\": \"b\"}");

    //then
    try {
      final OperateEntity operateEntity = entityStorage.getOperateEntititesQueue(topicName).poll(QUEUE_POLL_TIMEOUT, TimeUnit.MILLISECONDS);
      assertThat(operateEntity).isInstanceOf(WorkflowInstanceEntity.class);
      WorkflowInstanceEntity workflowInstanceEntity = (WorkflowInstanceEntity) operateEntity;
      assertThat(workflowInstanceEntity.getWorkflowDefinitionId()).isEqualTo(workflowId);
      assertThat(workflowInstanceEntity.getId()).isEqualTo(workflowInstanceId);
      assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
      assertThat(workflowInstanceEntity.getEndDate()).isNull();
      assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
      assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    } catch (InterruptedException e) {
      fail("No entities ar loaded from Zeebe");
    }

  }

}
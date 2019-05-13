/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.deployment;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.util.StreamProcessorControl;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DeploymentCreatedProcessorTest {
  public static final String PROCESS_ID = "process";
  public static final String RESOURCE_ID = "process.bpmn";
  public static final String MESSAGE_NAME = "msg";

  @Rule
  public StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION + 1);

  private StreamProcessorControl streamProcessor;
  private WorkflowState workflowState;

  @Before
  public void setUp() {
    streamProcessor =
        rule.initTypedStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
              final ZeebeState zeebeState = new ZeebeState(zeebeDb, dbContext);
              workflowState = zeebeState.getWorkflowState();

              DeploymentEventProcessors.addDeploymentCreateProcessor(
                  typedEventStreamProcessorBuilder, workflowState);
              typedEventStreamProcessorBuilder.onEvent(
                  ValueType.DEPLOYMENT,
                  DeploymentIntent.CREATED,
                  new DeploymentCreatedProcessor(workflowState, false));

              return typedEventStreamProcessorBuilder.build();
            });
  }

  @Test
  public void shouldNotFailIfCantFindPreviousVersion() {
    // given
    streamProcessor.start();

    // when
    writeMessageStartRecord(1, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().exists());
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .limit(1)
                .getFirst()
                .getMetadata()
                .getIntent())
        .isEqualTo(MessageStartEventSubscriptionIntent.OPEN);
  }

  @Test
  public void shouldNotWriteCloseSubscriptionIfNotMessageStart() {
    // given
    streamProcessor.start();

    // when
    writeNoneStartRecord(3, 1);
    writeMessageStartRecord(7, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().exists());
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .limit(1)
                .getFirst()
                .getMetadata()
                .getIntent())
        .isEqualTo(MessageStartEventSubscriptionIntent.OPEN);
  }

  @Test
  public void shouldCloseSubscriptionWhenInCorrectOrder() {
    // given
    streamProcessor.start();

    // when
    writeMessageStartRecord(3, 1);
    waitUntil(
        () -> rule.events().onlyDeploymentRecords().withIntent(DeploymentIntent.CREATED).exists());
    writeNoneStartRecord(7, 2);

    // then
    waitUntil(() -> rule.events().onlyMessageStartEventSubscriptionRecords().count() == 2);

    final TypedRecord<MessageStartEventSubscriptionRecord> closeRecord =
        rule.events()
            .onlyMessageStartEventSubscriptionRecords()
            .withIntent(MessageStartEventSubscriptionIntent.CLOSE)
            .getFirst();

    Assertions.assertThat(closeRecord.getValue().getWorkflowKey()).isEqualTo(3);
  }

  @Test
  public void shouldIgnoreOutdatedDeployment() {
    // given
    streamProcessor.start();

    // when
    streamProcessor.blockAfterMessageStartEventSubscriptionRecord(
        r ->
            r.getValue().getWorkflowKey() == 5
                && r.getMetadata().getIntent() == MessageStartEventSubscriptionIntent.OPEN);
    writeMessageStartRecord(5, 2);
    waitUntil(() -> streamProcessor.isBlocked());

    streamProcessor.blockAfterDeploymentEvent(
        r -> r.getKey() == 3 && r.getMetadata().getIntent() == DeploymentIntent.CREATED);
    writeMessageStartRecord(3, 1);
    streamProcessor.unblock();
    waitUntil(() -> streamProcessor.isBlocked());

    // then
    Assertions.assertThat(
            rule.getZeebeState()
                .getWorkflowState()
                .getLatestWorkflowVersionByProcessId(BufferUtil.wrapString(PROCESS_ID))
                .getVersion())
        .isEqualTo(2);
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.OPEN)
                .count())
        .isEqualTo(1);
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CLOSE))
        .isNullOrEmpty();
  }

  @Test
  public void shouldCloseSubscriptionEvenIfNotNextVersion() {
    // given
    streamProcessor.start();

    // when
    streamProcessor.blockAfterMessageStartEventSubscriptionRecord(
        r ->
            r.getValue().getWorkflowKey() == 3
                && r.getMetadata().getIntent() == MessageStartEventSubscriptionIntent.OPEN);
    writeMessageStartRecord(3, 1);
    waitUntil(() -> streamProcessor.isBlocked());

    streamProcessor.blockAfterMessageStartEventSubscriptionRecord(
        r ->
            r.getValue().getWorkflowKey() == 3
                && r.getMetadata().getIntent() == MessageStartEventSubscriptionIntent.CLOSE);
    writeNoneStartRecord(7, 3);
    streamProcessor.unblock();
    waitUntil(() -> streamProcessor.isBlocked());

    streamProcessor.blockAfterDeploymentEvent(
        r -> r.getKey() == 5 && r.getMetadata().getIntent() == DeploymentIntent.CREATED);
    writeMessageStartRecord(5, 2);
    streamProcessor.unblock();
    waitUntil(() -> streamProcessor.isBlocked());

    // then
    Assertions.assertThat(
            rule.events()
                .onlyMessageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CLOSE)
                .count())
        .isEqualTo(1);
  }

  private void writeNoneStartRecord(final long key, final int version) {
    writeNoneStartRecord(PROCESS_ID, RESOURCE_ID, key, version);
  }

  private void writeNoneStartRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final DeploymentRecord record =
        createNoneStartDeploymentRecord(processId, resourceId, key, version);

    rule.writeCommand(key, DeploymentIntent.CREATE, record);
  }

  private void writeMessageStartRecord(final long key, final int version) {
    writeMessageStartRecord(PROCESS_ID, RESOURCE_ID, key, version);
  }

  private void writeMessageStartRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final DeploymentRecord msgRecord =
        createMessageStartDeploymentRecord(processId, resourceId, key, version);
    rule.writeCommand(key, DeploymentIntent.CREATE, msgRecord);
  }

  private static DeploymentRecord createMessageStartDeploymentRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .message(MESSAGE_NAME)
            .endEvent()
            .done();
    return createDeploymentRecord(modelInstance, processId, resourceId, key, version);
  }

  private static DeploymentRecord createNoneStartDeploymentRecord(
      final String processId, final String resourceId, final long key, final int version) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    return createDeploymentRecord(modelInstance, processId, resourceId, key, version);
  }

  private static DeploymentRecord createDeploymentRecord(
      final BpmnModelInstance modelInstance,
      final String processId,
      final String resourceId,
      final long key,
      final int version) {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceId))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    deploymentRecord
        .workflows()
        .add()
        .setKey(key)
        .setBpmnProcessId(processId)
        .setResourceName(resourceId)
        .setVersion(version);

    return deploymentRecord;
  }
}

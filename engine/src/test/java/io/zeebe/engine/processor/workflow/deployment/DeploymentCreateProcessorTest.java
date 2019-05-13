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
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.deployment.ResourceType;
import io.zeebe.protocol.intent.DeploymentIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class DeploymentCreateProcessorTest {
  @Rule
  public StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION + 1);

  private StreamProcessorControl streamProcessor;
  private WorkflowState workflowState;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    streamProcessor =
        rule.initTypedStreamProcessor(
            (typedEventStreamProcessorBuilder, zeebeDb, dbContext) -> {
              final ZeebeState zeebeState = new ZeebeState(zeebeDb, dbContext);
              workflowState = zeebeState.getWorkflowState();

              DeploymentEventProcessors.addDeploymentCreateProcessor(
                  typedEventStreamProcessorBuilder, workflowState);
              return typedEventStreamProcessorBuilder.build();
            });
  }

  @Test
  public void shouldRejectTwoCreatingCommands() {
    // given
    streamProcessor.blockAfterDeploymentEvent(
        r -> r.getMetadata().getIntent() == DeploymentIntent.CREATE);
    streamProcessor.start();

    creatingDeployment();
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    creatingDeployment();
    streamProcessor.unblock();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 4);

    final List<TypedRecord<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());
    //
    Assertions.assertThat(collect)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED,
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATE);
    //
    Assertions.assertThat(collect)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldNotRejectTwoCreatingCommandsWithDifferentKeys() {
    // given
    streamProcessor.blockAfterDeploymentEvent(
        r -> r.getMetadata().getIntent() == DeploymentIntent.CREATE);
    streamProcessor.start();

    creatingDeployment(4);
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    creatingDeployment(8);
    streamProcessor.unblock();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 4);

    final List<TypedRecord<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());

    Assertions.assertThat(collect)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED,
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED);

    Assertions.assertThat(collect)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.EVENT);
  }

  private void creatingDeployment() {
    creatingDeployment(4);
  }

  private void creatingDeployment(final long key) {
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord();

    rule.writeCommand(key, DeploymentIntent.CREATE, deploymentRecord);
  }

  public static DeploymentRecord creatingDeploymentRecord() {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("processId")
            .startEvent()
            .serviceTask(
                "test",
                task -> {
                  task.zeebeTaskType("type");
                })
            .endEvent()
            .done();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    return deploymentRecord;
  }
}

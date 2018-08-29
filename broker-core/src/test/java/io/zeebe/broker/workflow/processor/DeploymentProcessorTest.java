/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.system.workflow.repository.data.DeploymentRecord;
import io.zeebe.broker.system.workflow.repository.data.ResourceType;
import io.zeebe.broker.system.workflow.repository.processor.DeploymentStreamProcessor;
import io.zeebe.broker.system.workflow.repository.processor.DeploymentTransformer;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.broker.topic.StreamProcessorControl;
import io.zeebe.broker.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.transport.ClientTransport;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DeploymentProcessorTest {

  @Rule public StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION);

  @Mock TopologyManager topologyManager;
  @Mock ClientTransport managementApi;

  private StreamProcessorControl streamProcessor;
  private WorkflowRepositoryIndex workflowRepositoryIndex;
  private DeploymentStreamProcessor deploymentStreamProcessor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    workflowRepositoryIndex = new WorkflowRepositoryIndex();
    deploymentStreamProcessor =
        new DeploymentStreamProcessor(workflowRepositoryIndex, topologyManager, managementApi);

    streamProcessor =
        rule.initStreamProcessor(env -> deploymentStreamProcessor.createStreamProcessor(env));
  }

  @Test
  public void shouldRejectTwoCreatingCommands() {
    // given
    streamProcessor.blockAfterDeploymentEvent(
        r -> r.getMetadata().getIntent() == DeploymentIntent.CREATING);

    creatingDeployment();
    streamProcessor.start();
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    creatingDeployment();
    streamProcessor.unblock();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 4);

    final List<TypedRecord<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());

    assertThat(collect)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            DeploymentIntent.CREATING,
            DeploymentIntent.CREATED,
            DeploymentIntent.CREATING,
            DeploymentIntent.CREATING);

    assertThat(collect)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.COMMAND_REJECTION);
  }

  @Test
  public void shouldNotRejectTwoCreatingCommandsWithDifferentKeys() {
    // given
    streamProcessor.blockAfterDeploymentEvent(
        r -> r.getMetadata().getIntent() == DeploymentIntent.CREATING);

    creatingDeployment(4);
    streamProcessor.start();
    waitUntil(() -> streamProcessor.isBlocked());

    // when
    creatingDeployment(8);
    streamProcessor.unblock();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 4);

    final List<TypedRecord<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());

    assertThat(collect)
        .extracting(r -> r.getMetadata().getIntent())
        .containsExactly(
            DeploymentIntent.CREATING,
            DeploymentIntent.CREATED,
            DeploymentIntent.CREATING,
            DeploymentIntent.CREATED);

    assertThat(collect)
        .extracting(r -> r.getMetadata().getRecordType())
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.EVENT);
  }

  private void creatingDeployment() {
    creatingDeployment(4);
  }

  private void creatingDeployment(long key) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess()
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

    final DeploymentTransformer deploymentTransformer =
        new DeploymentTransformer(deploymentStreamProcessor.getRepositoryIndex());

    deploymentTransformer.transform(deploymentRecord);

    rule.writeCommand(key, DeploymentIntent.CREATING, deploymentRecord);
  }
}

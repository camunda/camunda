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

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.api.record.value.deployment.DeploymentResource;
import io.zeebe.exporter.api.record.value.deployment.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Test;

public class CreateDeploymentMultiplePartitionsTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();

  public static final int PARTITION_ID = DEPLOYMENT_PARTITION;
  public static final int PARTITION_COUNT = 3;

  @ClassRule public static final EngineRule ENGINE = new EngineRule(PARTITION_COUNT);

  @Test
  public void shouldCreateDeploymentOnAllPartitions() {
    // when
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldCreateDeploymentOnAllPartitions")
            .startEvent()
            .endEvent()
            .done();
    final Record<DeploymentRecordValue> deployment =
        ENGINE.deployment().withXmlResource("process.bpmn", modelInstance).deploy();

    // then
    assertThat(deployment.getKey()).isGreaterThanOrEqualTo(0L);

    final RecordMetadata metadata = deployment.getMetadata();
    assertThat(metadata.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(metadata.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);

    ENGINE
        .getPartitionIds()
        .forEach(
            partitionId ->
                assertCreatedDeploymentEventResources(
                    partitionId,
                    deployment.getKey(),
                    (createdDeployment) -> {
                      final DeploymentResource resource =
                          createdDeployment.getValue().getResources().get(0);

                      Assertions.assertThat(resource)
                          .hasResource(bpmnXml(WORKFLOW))
                          .hasResourceType(ResourceType.BPMN_XML);

                      final List<DeployedWorkflow> deployedWorkflows =
                          createdDeployment.getValue().getDeployedWorkflows();

                      assertThat(deployedWorkflows).hasSize(1);
                      Assertions.assertThat(deployedWorkflows.get(0))
                          .hasBpmnProcessId("shouldCreateDeploymentOnAllPartitions")
                          .hasVersion(1)
                          .hasWorkflowKey(getDeployedWorkflow(deployment, 0).getWorkflowKey())
                          .hasResourceName("process.bpmn");
                    }));
  }

  @Test
  public void shouldOnlyDistributeFromDeploymentPartition() {
    // when
    final long deploymentKey1 = ENGINE.deployment().withXmlResource(WORKFLOW).deploy().getKey();

    // then
    final List<Record<DeploymentRecordValue>> deploymentRecords =
        RecordingExporter.deploymentRecords()
            .withRecordKey(deploymentKey1)
            .limit(r -> r.getMetadata().getIntent() == DeploymentIntent.DISTRIBUTED)
            .withIntent(DeploymentIntent.DISTRIBUTE)
            .asList();

    assertThat(deploymentRecords).hasSize(1);
    assertThat(deploymentRecords.get(0).getMetadata().getPartitionId())
        .isEqualTo(DEPLOYMENT_PARTITION);
  }

  @Test
  public void shouldCreateDeploymentWithYamlResourcesOnAllPartitions() throws Exception {
    // given
    final Path yamlFile =
        Paths.get(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final byte[] yamlWorkflow = Files.readAllBytes(yamlFile);

    // when
    final Record<DeploymentRecordValue> distributedDeployment =
        ENGINE.deployment().withYamlResource("simple-workflow.yaml", yamlWorkflow).deploy();

    // then
    org.assertj.core.api.Assertions.assertThat(distributedDeployment.getKey())
        .isGreaterThanOrEqualTo(0L);

    final RecordMetadata metadata = distributedDeployment.getMetadata();

    assertThat(metadata.getPartitionId()).isEqualTo(PARTITION_ID);
    assertThat(metadata.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(metadata.getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);

    ENGINE
        .getPartitionIds()
        .forEach(
            partitionId ->
                assertCreatedDeploymentEventResources(
                    partitionId,
                    distributedDeployment.getKey(),
                    (deploymentCreatedEvent) -> {
                      final DeploymentRecordValue deployment = deploymentCreatedEvent.getValue();
                      final DeploymentResource resource = deployment.getResources().get(0);
                      Assertions.assertThat(resource).hasResourceType(ResourceType.YAML_WORKFLOW);

                      final List<DeployedWorkflow> deployedWorkflows =
                          deployment.getDeployedWorkflows();
                      assertThat(deployedWorkflows).hasSize(1);

                      Assertions.assertThat(deployedWorkflows.get(0))
                          .hasBpmnProcessId("yaml-workflow")
                          .hasVersion(1)
                          .hasWorkflowKey(
                              getDeployedWorkflow(distributedDeployment, 0).getWorkflowKey())
                          .hasResourceName("simple-workflow.yaml");
                    }));
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleWorkflows() {
    // given

    // when
    final Record<DeploymentRecordValue> deployment =
        ENGINE
            .deployment()
            .withXmlResource("process.bpmn", WORKFLOW)
            .withXmlResource("process2.bpmn", WORKFLOW_2)
            .deploy();

    // then
    assertThat(deployment.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(deployment.getMetadata().getIntent()).isEqualTo(DeploymentIntent.DISTRIBUTED);

    final List<Record<DeploymentRecordValue>> createdDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(deployment.getKey())
            .limit(PARTITION_COUNT)
            .asList();

    assertThat(createdDeployments)
        .hasSize(PARTITION_COUNT)
        .extracting(Record::getValue)
        .flatExtracting(DeploymentRecordValue::getDeployedWorkflows)
        .extracting(DeployedWorkflow::getBpmnProcessId)
        .containsOnly("process", "process2");
  }

  @Test
  public void shouldIncrementWorkflowVersions() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("shouldIncrementWorkflowVersions")
            .startEvent()
            .endEvent()
            .done();
    final Record<DeploymentRecordValue> firstDeployment =
        ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // when
    final Record<DeploymentRecordValue> secondDeployment =
        ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // then
    final List<Record<DeploymentRecordValue>> firstCreatedDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(firstDeployment.getKey())
            .limit(PARTITION_COUNT)
            .asList();

    assertThat(firstCreatedDeployments)
        .hasSize(PARTITION_COUNT)
        .extracting(Record::getValue)
        .flatExtracting(DeploymentRecordValue::getDeployedWorkflows)
        .extracting(DeployedWorkflow::getVersion)
        .containsOnly(1);

    final List<Record<DeploymentRecordValue>> secondCreatedDeployments =
        RecordingExporter.deploymentRecords()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(secondDeployment.getKey())
            .limit(PARTITION_COUNT)
            .asList();

    assertThat(secondCreatedDeployments)
        .hasSize(PARTITION_COUNT)
        .extracting(Record::getValue)
        .flatExtracting(DeploymentRecordValue::getDeployedWorkflows)
        .extracting(DeployedWorkflow::getVersion)
        .containsOnly(2);
  }

  private byte[] bpmnXml(final BpmnModelInstance definition) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, definition);
    return outStream.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private DeployedWorkflow getDeployedWorkflow(
      final Record<DeploymentRecordValue> record, final int offset) {
    return record.getValue().getDeployedWorkflows().get(offset);
  }

  private void assertCreatedDeploymentEventResources(
      final int expectedPartition,
      final long expectedKey,
      final Consumer<Record<DeploymentRecordValue>> deploymentAssert) {
    final Record deploymentCreatedEvent =
        RecordingExporter.deploymentRecords()
            .withPartitionId(expectedPartition)
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(expectedKey)
            .getFirst();

    assertThat(deploymentCreatedEvent.getKey()).isEqualTo(expectedKey);
    assertThat(deploymentCreatedEvent.getMetadata().getPartitionId()).isEqualTo(expectedPartition);

    deploymentAssert.accept(deploymentCreatedEvent);
  }
}

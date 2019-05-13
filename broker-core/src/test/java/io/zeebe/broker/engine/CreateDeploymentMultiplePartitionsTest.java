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
package io.zeebe.broker.engine;

import static io.zeebe.protocol.Protocol.DEPLOYMENT_PARTITION;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.UnstableCI;
import io.zeebe.broker.test.EmbeddedBrokerConfigurator;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.deployment.DeployedWorkflow;
import io.zeebe.exporter.api.record.value.deployment.DeploymentResource;
import io.zeebe.exporter.api.record.value.deployment.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.util.record.RecordingExporter;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;

public class CreateDeploymentMultiplePartitionsTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  private static final BpmnModelInstance WORKFLOW_2 =
      Bpmn.createExecutableProcess("process2").startEvent().endEvent().done();

  public static final int PARTITION_ID = DEPLOYMENT_PARTITION;
  public static final int PARTITION_COUNT = 3;

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(EmbeddedBrokerConfigurator.setPartitionCount(PARTITION_COUNT));

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getAtomix);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  public static final long FIRST_DEPLOYED_WORKFLOW_KEY =
      Protocol.encodePartitionId(PARTITION_ID, 1);

  @Test
  public void shouldCreateDeploymentOnAllPartitions() {
    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(Protocol.DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put(
                "resources",
                Collections.singletonList(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn")))
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getKey()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.getPartitionId()).isEqualTo(PARTITION_ID);

    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    apiRule
        .getPartitionIds()
        .forEach(
            partitionId -> assertCreatedDeploymentEventOnPartition(partitionId, resp.getKey()));
  }

  @Test
  public void shouldOnlyDistributeFromDeploymentPartition() {
    // when
    final long deploymentKey1 = apiRule.deployWorkflow(WORKFLOW).getKey();
    final long deploymentKey2 = apiRule.deployWorkflow(WORKFLOW).getKey();

    // then
    for (int partitionId = 0; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.deploymentRecords()
                  .withPartitionId(partitionId)
                  .limit(r -> r.getKey() == deploymentKey2)
                  .withIntent(DeploymentIntent.DISTRIBUTE)
                  .withRecordKey(deploymentKey1)
                  .exists())
          .isEqualTo(partitionId == DEPLOYMENT_PARTITION);
    }
  }

  @Test
  public void shouldCreateDeploymentWithYamlResourcesOnAllPartitions() throws Exception {
    // given
    final Path yamlFile =
        Paths.get(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final byte[] yamlWorkflow = Files.readAllBytes(yamlFile);

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .partitionClient()
            .deployWithResponse(
                yamlWorkflow, ResourceType.YAML_WORKFLOW.name(), "simple-workflow.yaml");

    // then
    assertThat(resp.getKey()).isGreaterThanOrEqualTo(0L);
    assertThat(resp.getPartitionId()).isEqualTo(PARTITION_ID);

    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    final Map<String, Object> resources = deploymentResource(yamlWorkflow, "simple-workflow.yaml");
    resources.put("resourceType", ResourceType.YAML_WORKFLOW.name());

    apiRule
        .getPartitionIds()
        .forEach(
            partitionId ->
                assertCreatedDeploymentEventResources(
                    partitionId,
                    resp.getKey(),
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
                          .hasWorkflowKey(FIRST_DEPLOYED_WORKFLOW_KEY)
                          .hasResourceName("simple-workflow.yaml");
                    }));
  }

  @Test
  public void shouldCreateDeploymentResourceWithMultipleWorkflows() {
    // given
    final List<Map<String, Object>> resources = new ArrayList<>();
    resources.add(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn"));
    resources.add(deploymentResource(bpmnXml(WORKFLOW_2), "process2.bpmn"));

    // when
    final ExecuteCommandResponse resp =
        apiRule
            .createCmdRequest()
            .partitionId(DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put("resources", resources)
            .done()
            .sendAndAwait();

    // then
    assertThat(resp.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(resp.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    apiRule
        .getPartitionIds()
        .forEach(
            partitionId ->
                assertCreatedDeploymentEventResources(
                    partitionId,
                    resp.getKey(),
                    (createdDeployment) -> {
                      final List<DeployedWorkflow> deployedWorkflows =
                          Arrays.asList(
                              getDeployedWorkflow(createdDeployment, 0),
                              getDeployedWorkflow(createdDeployment, 1));
                      assertThat(deployedWorkflows)
                          .extracting(s -> s.getBpmnProcessId())
                          .contains("process", "process2");
                    }));
  }

  @Test
  public void shouldIncrementWorkflowVersions() {
    // given
    final ExecuteCommandResponse d1 = apiRule.partitionClient().deployWithResponse(WORKFLOW);
    apiRule
        .partitionClient()
        .receiveFirstDeploymentEvent(DeploymentIntent.DISTRIBUTED, d1.getKey());

    // when
    final ExecuteCommandResponse d2 = apiRule.partitionClient().deployWithResponse(WORKFLOW);

    // then
    final Map<String, Object> workflow1 = getDeployedWorkflow(d1, 0);
    assertThat(workflow1.get("version")).isEqualTo(1L);

    apiRule
        .getPartitionIds()
        .forEach(
            partitionId ->
                assertCreatedDeploymentEventResources(
                    partitionId,
                    d1.getKey(),
                    createdDeployment -> {
                      assertThat(getDeployedWorkflow(createdDeployment, 0).getVersion())
                          .isEqualTo(1L);
                    }));

    final Map<String, Object> workflow2 = getDeployedWorkflow(d2, 0);
    assertThat(workflow2.get("version")).isEqualTo(2L);

    apiRule
        .getPartitionIds()
        .forEach(
            partitionId ->
                assertCreatedDeploymentEventResources(
                    partitionId,
                    d2.getKey(),
                    createdDeployment ->
                        assertThat(getDeployedWorkflow(createdDeployment, 0).getVersion())
                            .isEqualTo(2L)));
  }

  @Test
  @Category(UnstableCI.class) // => https://github.com/zeebe-io/zeebe/issues/2110
  public void shouldCreateDeploymentOnAllPartitionsWithRestartBroker() {
    // given
    apiRule
        .createCmdRequest()
        .partitionId(Protocol.DEPLOYMENT_PARTITION)
        .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
        .command()
        .put(
            "resources",
            Collections.singletonList(deploymentResource(bpmnXml(WORKFLOW), "process.bpmn")))
        .done()
        .send()
        .await();

    // when
    brokerRule.restartBroker();
    apiRule.restart();
    doRepeatedly(apiRule::getPartitionIds).until(p -> !p.isEmpty());

    // then
    apiRule
        .getPartitionIds()
        .forEach(partitionId -> assertAnyCreatedDeploymentEventOnPartition(partitionId));
  }

  private Map<String, Object> deploymentResource(final byte[] resource, final String name) {
    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", ResourceType.BPMN_XML);
    deploymentResource.put("resourceName", name);

    return deploymentResource;
  }

  private byte[] bpmnXml(final BpmnModelInstance definition) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, definition);
    return outStream.toByteArray();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getDeployedWorkflow(
      final ExecuteCommandResponse d1, final int offset) {
    final List<Map<String, Object>> d1Workflows =
        (List<Map<String, Object>>) d1.getValue().get("workflows");
    return d1Workflows.get(offset);
  }

  @SuppressWarnings("unchecked")
  private DeployedWorkflow getDeployedWorkflow(
      final Record<DeploymentRecordValue> record, final int offset) {
    return record.getValue().getDeployedWorkflows().get(offset);
  }

  private void assertCreatedDeploymentEventOnPartition(
      final int expectedPartition, final long expectedKey) {
    assertCreatedDeploymentEventResources(
        expectedPartition,
        expectedKey,
        (deploymentCreatedEvent) -> {
          assertThat(deploymentCreatedEvent.getKey()).isEqualTo(expectedKey);
          assertThat(deploymentCreatedEvent.getMetadata().getPartitionId())
              .isEqualTo(expectedPartition);

          assertDeploymentRecord(deploymentCreatedEvent);
        });
  }

  private void assertAnyCreatedDeploymentEventOnPartition(final int expectedPartition) {
    assertAnyCreatedDeploymentEventResources(
        expectedPartition,
        (deploymentCreatedEvent) -> {
          assertThat(deploymentCreatedEvent.getMetadata().getPartitionId())
              .isEqualTo(expectedPartition);

          assertDeploymentRecord(deploymentCreatedEvent);
        });
  }

  private void assertDeploymentRecord(final Record<DeploymentRecordValue> deploymentCreatedEvent) {

    final DeploymentResource resource = deploymentCreatedEvent.getValue().getResources().get(0);

    Assertions.assertThat(resource)
        .hasResource(bpmnXml(WORKFLOW))
        .hasResourceType(ResourceType.BPMN_XML);

    final List<DeployedWorkflow> deployedWorkflows =
        deploymentCreatedEvent.getValue().getDeployedWorkflows();

    assertThat(deployedWorkflows).hasSize(1);
    Assertions.assertThat(deployedWorkflows.get(0))
        .hasBpmnProcessId("process")
        .hasVersion(1)
        .hasWorkflowKey(FIRST_DEPLOYED_WORKFLOW_KEY)
        .hasResourceName("process.bpmn");
  }

  private void assertCreatedDeploymentEventResources(
      final int expectedPartition,
      final long expectedKey,
      final Consumer<Record<DeploymentRecordValue>> deploymentAssert) {
    final Record deploymentCreatedEvent =
        apiRule
            .partitionClient(expectedPartition)
            .receiveDeployments()
            .withIntent(DeploymentIntent.CREATED)
            .withRecordKey(expectedKey)
            .getFirst();

    assertThat(deploymentCreatedEvent.getKey()).isEqualTo(expectedKey);
    assertThat(deploymentCreatedEvent.getMetadata().getPartitionId()).isEqualTo(expectedPartition);

    deploymentAssert.accept(deploymentCreatedEvent);
  }

  private void assertAnyCreatedDeploymentEventResources(
      final int expectedPartition, final Consumer<Record> deploymentAssert) {
    final Record deploymentCreatedEvent =
        apiRule
            .partitionClient(expectedPartition)
            .receiveDeployments()
            .withIntent(DeploymentIntent.CREATED)
            .getFirst();

    assertThat(deploymentCreatedEvent.getMetadata().getPartitionId()).isEqualTo(expectedPartition);

    deploymentAssert.accept(deploymentCreatedEvent);
  }
}

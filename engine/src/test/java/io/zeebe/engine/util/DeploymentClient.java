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
package io.zeebe.engine.util;

import static io.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.deployment.ResourceType;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class DeploymentClient {

  private static final BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>>
      SUCCESS_EXPECTATION =
          (sourceRecordPosition, forEachPartition) -> {
            final Record<DeploymentRecordValue> deploymentOnPartitionOne =
                RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
                    .withSourceRecordPosition(sourceRecordPosition)
                    .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                    .getFirst();

            forEachPartition.accept(
                partitionId ->
                    RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
                        .withPartitionId(partitionId)
                        .withRecordKey(deploymentOnPartitionOne.getKey())
                        .getFirst());

            return RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
                .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                .withRecordKey(deploymentOnPartitionOne.getKey())
                .getFirst();
          };

  private static final BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>>
      REJECTION_EXPECTATION =
          (sourceRecordPosition, forEachPartition) ->
              RecordingExporter.deploymentRecords(DeploymentIntent.CREATE)
                  .onlyCommandRejections()
                  .withSourceRecordPosition(sourceRecordPosition)
                  .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
                  .getFirst();

  private final StreamProcessorRule environmentRule;
  private final DeploymentRecord deploymentRecord;
  private final Consumer<Consumer<Integer>> forEachPartition;

  private BiFunction<Long, Consumer<Consumer<Integer>>, Record<DeploymentRecordValue>> expectation =
      SUCCESS_EXPECTATION;

  public DeploymentClient(
      StreamProcessorRule environmentRule, Consumer<Consumer<Integer>> forEachPartition) {
    this.environmentRule = environmentRule;
    this.forEachPartition = forEachPartition;
    deploymentRecord = new DeploymentRecord();
  }

  public DeploymentClient withYamlResource(byte[] resource) {
    return withYamlResource("process.yaml", resource);
  }

  public DeploymentClient withYamlResource(String resourceName, byte[] resource) {
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapArray(resource))
        .setResourceType(ResourceType.YAML_WORKFLOW);
    return this;
  }

  public DeploymentClient withXmlResource(BpmnModelInstance modelInstance) {
    return withXmlResource("process.xml", modelInstance);
  }

  public DeploymentClient withXmlResource(byte[] resourceBytes) {
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.xml"))
        .setResource(wrapArray(resourceBytes))
        .setResourceType(ResourceType.BPMN_XML);
    return this;
  }

  public DeploymentClient withXmlResource(String resourceName, BpmnModelInstance modelInstance) {
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString(resourceName))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);
    return this;
  }

  public DeploymentClient expectRejection() {
    this.expectation = REJECTION_EXPECTATION;
    return this;
  }

  public Record<DeploymentRecordValue> deploy() {
    final long position = environmentRule.writeCommand(DeploymentIntent.CREATE, deploymentRecord);

    return expectation.apply(position, forEachPartition);
  }
}

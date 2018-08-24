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
package io.zeebe.broker.system.workflow.repository.processor;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.system.workflow.repository.processor.state.DeploymentsStateController;
import io.zeebe.broker.system.workflow.repository.processor.state.WorkflowRepositoryIndex;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.processor.StreamProcessorContext;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.transport.ClientTransport;
import java.util.function.Consumer;

public class DeploymentStreamProcessor implements StreamProcessorLifecycleAware {

  private Consumer<StreamProcessorContext> onRecoveredCallback;
  private Runnable onClosedCallback;
  private DeploymentsStateController deploymentsStateController;
  private final WorkflowRepositoryIndex repositoryIndex;
  private TopologyManager topologyManager;
  private ClientTransport managementApi;

  public DeploymentStreamProcessor(
      WorkflowRepositoryIndex workflowRepositoryIndex,
      TopologyManager topologyManager,
      ClientTransport managementApi) {
    this((ctx) -> {}, () -> {}, workflowRepositoryIndex, topologyManager, managementApi);
  }

  public DeploymentStreamProcessor(
      Consumer<StreamProcessorContext> onRecoveredCallback,
      Runnable onClosedCallback,
      WorkflowRepositoryIndex workflowRepositoryIndex,
      TopologyManager topologyManager,
      ClientTransport managementApi) {
    this.onRecoveredCallback = onRecoveredCallback;
    this.onClosedCallback = onClosedCallback;
    this.topologyManager = topologyManager;
    this.managementApi = managementApi;
    this.repositoryIndex = workflowRepositoryIndex;
    this.deploymentsStateController = new DeploymentsStateController();
  }

  public StreamProcessor createStreamProcessor(TypedStreamEnvironment streamEnvironment) {
    final TypedEventStreamProcessorBuilder streamProcessorBuilder =
        streamEnvironment
            .newStreamProcessor()
            .onCommand(
                ValueType.DEPLOYMENT,
                DeploymentIntent.CREATING,
                new DeploymentCreatingProcessor(deploymentsStateController))
            .onEvent(
                ValueType.DEPLOYMENT,
                DeploymentIntent.CREATED,
                new DeploymentCreatedProcessor(repositoryIndex))
            .withStateController(deploymentsStateController)
            .withStateResource(repositoryIndex);

    final LogStream stream = streamEnvironment.getStream();
    final int partitionId = stream.getPartitionId();
    if (partitionId == Protocol.DEPLOYMENT_PARTITION) {

      final LogStreamWriterImpl logStreamWriter = new LogStreamWriterImpl(stream);

      final DeploymentCreateProcessor createEventProcessor =
          new DeploymentCreateProcessor(repositoryIndex);
      final DeploymentDistributeProcessor deploymentDistributeProcessor =
          new DeploymentDistributeProcessor(
              topologyManager, deploymentsStateController, managementApi, logStreamWriter);

      streamProcessorBuilder
          .withListener(this)
          .keyGenerator(KeyGenerator.createDeploymentKeyGenerator(deploymentsStateController))
          .onCommand(ValueType.DEPLOYMENT, DeploymentIntent.CREATE, createEventProcessor)
          .onEvent(ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor)
          .onRejection(
              ValueType.DEPLOYMENT, DeploymentIntent.CREATE, new DeploymentRejectedProcessor());
    }
    return streamProcessorBuilder.build();
  }

  public StateSnapshotController createStateSnapshotController(StateStorage stateStorage) {
    return new StateSnapshotController(deploymentsStateController, stateStorage);
  }

  public WorkflowRepositoryIndex getRepositoryIndex() {
    return repositoryIndex;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {}

  @Override
  public void onRecovered(TypedStreamProcessor streamProcessor) {
    final StreamProcessorContext ctx = streamProcessor.getStreamProcessorContext();
    onRecoveredCallback.accept(ctx);
  }

  @Override
  public void onClose() {
    onClosedCallback.run();
  }
}

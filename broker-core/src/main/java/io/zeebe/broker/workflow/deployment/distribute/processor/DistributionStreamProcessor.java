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
package io.zeebe.broker.workflow.deployment.distribute.processor;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.workflow.deployment.distribute.processor.state.DeploymentsStateController;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.processor.StreamProcessor;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.transport.ClientTransport;

public class DistributionStreamProcessor implements StreamProcessorLifecycleAware {

  private final DeploymentsStateController deploymentsStateController;
  private final TopologyManager topologyManager;
  private final ClientTransport managementApi;
  private final ClusterCfg clusterCfg;

  public DistributionStreamProcessor(
      final ClusterCfg clusterCfg,
      final TopologyManager topologyManager,
      final ClientTransport managementApi) {
    this.clusterCfg = clusterCfg;
    this.topologyManager = topologyManager;
    this.managementApi = managementApi;
    this.deploymentsStateController = new DeploymentsStateController();
  }

  public StreamProcessor createStreamProcessor(final TypedStreamEnvironment streamEnvironment) {
    final LogStream stream = streamEnvironment.getStream();
    final LogStreamWriterImpl logStreamWriter = new LogStreamWriterImpl(stream);

    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            clusterCfg,
            topologyManager,
            deploymentsStateController,
            managementApi,
            logStreamWriter);

    return streamEnvironment
        .newStreamProcessor()
        .withStateController(deploymentsStateController)
        .withListener(this)
        .onEvent(ValueType.DEPLOYMENT, DeploymentIntent.CREATED, new DeploymentCreatedProcessor())
        .onCommand(ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor)
        .build();
  }

  public StateSnapshotController createStateSnapshotController(final StateStorage stateStorage) {
    return new StateSnapshotController(deploymentsStateController, stateStorage);
  }
}

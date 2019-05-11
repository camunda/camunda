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
package io.zeebe.broker.logstreams;

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.incident.processor.IncidentEventProcessors;
import io.zeebe.broker.job.JobEventProcessors;
import io.zeebe.broker.logstreams.StreamProcessorServiceFactory.Builder;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.subscription.message.processor.MessageEventProcessors;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.transport.clientapi.CommandResponseWriterImpl;
import io.zeebe.broker.workflow.deployment.distribute.processor.DeploymentDistributeProcessor;
import io.zeebe.broker.workflow.processor.BpmnStepProcessor;
import io.zeebe.broker.workflow.processor.CatchEventBehavior;
import io.zeebe.broker.workflow.processor.WorkflowEventProcessors;
import io.zeebe.broker.workflow.processor.deployment.DeploymentCreatedProcessor;
import io.zeebe.broker.workflow.processor.deployment.DeploymentEventProcessors;
import io.zeebe.broker.workflow.processor.timer.DueDateTimerChecker;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.sched.ActorControl;

public class ZbStreamProcessorService implements Service<ZbStreamProcessorService> {

  public static final String PROCESSOR_NAME = "zb-stream-processor";

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();
  private final Injector<Atomix> atomixInjector = new Injector<>();

  private final ClusterCfg clusterCfg;
  private StreamProcessorServiceFactory streamProcessorServiceFactory;
  private ServerTransport clientApiTransport;
  private TopologyManager topologyManager;
  private Atomix atomix;
  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((partitionName, partition) -> startStreamProcessors(partitionName, partition))
          .build();

  public ZbStreamProcessorService(final ClusterCfg clusterCfg) {
    this.clusterCfg = clusterCfg;
  }

  @Override
  public void start(final ServiceStartContext serviceContext) {
    this.clientApiTransport = clientApiTransportInjector.getValue();
    this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
    this.topologyManager = topologyManagerInjector.getValue();
    this.atomix = atomixInjector.getValue();
  }

  public void startStreamProcessors(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {
    final int partitionId = partition.getPartitionId();

    final Builder streamProcessorServiceBuilder =
        streamProcessorServiceFactory
            .createService(partition, partitionServiceName)
            .processorId(partitionId)
            .processorName(PROCESSOR_NAME);

    streamProcessorServiceBuilder
        .snapshotController(partition.getProcessorSnapshotController())
        .streamProcessorFactory(
            (actor, zeebeDb, dbContext) -> {
              final ZeebeState zeebeState = new ZeebeState(partitionId, zeebeDb, dbContext);
              final TypedStreamEnvironment streamEnvironment =
                  new TypedStreamEnvironment(
                      partition.getLogStream(),
                      new CommandResponseWriterImpl(clientApiTransport.getOutput()));

              return createTypedStreamProcessor(actor, partitionId, streamEnvironment, zeebeState);
            })
        .deleteDataOnSnapshot(true)
        .build();
  }

  public TypedStreamProcessor createTypedStreamProcessor(
      ActorControl actor,
      int partitionId,
      TypedStreamEnvironment streamEnvironment,
      ZeebeState zeebeState) {
    final TypedEventStreamProcessorBuilder typedProcessorBuilder =
        streamEnvironment.newStreamProcessor().zeebeState(zeebeState);

    addDistributeDeploymentProcessors(actor, zeebeState, streamEnvironment, typedProcessorBuilder);

    final SubscriptionCommandSenderImpl subscriptionCommandSender =
        new SubscriptionCommandSenderImpl(atomix);
    subscriptionCommandSender.init(topologyManager, actor, streamEnvironment.getStream());
    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(
            zeebeState, subscriptionCommandSender, clusterCfg.getPartitionsCount());

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior, partitionId, zeebeState, typedProcessorBuilder);
    addMessageProcessors(subscriptionCommandSender, zeebeState, typedProcessorBuilder);

    final BpmnStepProcessor stepProcessor =
        addWorkflowProcessors(
            zeebeState, typedProcessorBuilder, subscriptionCommandSender, catchEventBehavior);
    addIncidentProcessors(zeebeState, stepProcessor, typedProcessorBuilder);
    addJobProcessors(zeebeState, typedProcessorBuilder);

    return typedProcessorBuilder.build();
  }

  private void addDistributeDeploymentProcessors(
      ActorControl actor,
      ZeebeState zeebeState,
      TypedStreamEnvironment streamEnvironment,
      TypedEventStreamProcessorBuilder typedEventStreamProcessorBuilder) {
    final LogStream stream = streamEnvironment.getStream();
    final LogStreamWriterImpl logStreamWriter = new LogStreamWriterImpl(stream);

    final TopologyPartitionListenerImpl partitionListener =
        new TopologyPartitionListenerImpl(actor);
    topologyManager.addTopologyPartitionListener(partitionListener);

    final DeploymentDistributorImpl deploymentDistributor =
        new DeploymentDistributorImpl(
            clusterCfg, atomix, partitionListener, zeebeState.getDeploymentState(), actor);
    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            zeebeState.getDeploymentState(), logStreamWriter, deploymentDistributor);

    typedEventStreamProcessorBuilder.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);
  }

  private BpmnStepProcessor addWorkflowProcessors(
      ZeebeState zeebeState,
      TypedEventStreamProcessorBuilder typedProcessorBuilder,
      SubscriptionCommandSenderImpl subscriptionCommandSender,
      CatchEventBehavior catchEventBehavior) {
    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getWorkflowState());
    return WorkflowEventProcessors.addWorkflowProcessors(
        typedProcessorBuilder,
        zeebeState,
        subscriptionCommandSender,
        catchEventBehavior,
        timerChecker);
  }

  public void addDeploymentRelatedProcessorAndServices(
      CatchEventBehavior catchEventBehavior,
      int partitionId,
      ZeebeState zeebeState,
      TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final boolean isDeploymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
    if (isDeploymentPartition) {
      DeploymentEventProcessors.addTransformingDeploymentProcessor(
          typedProcessorBuilder, zeebeState, catchEventBehavior);
    } else {
      DeploymentEventProcessors.addDeploymentCreateProcessor(typedProcessorBuilder, workflowState);
    }

    typedProcessorBuilder.onEvent(
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED,
        new DeploymentCreatedProcessor(workflowState, isDeploymentPartition));
  }

  private void addIncidentProcessors(
      ZeebeState zeebeState,
      BpmnStepProcessor stepProcessor,
      TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    IncidentEventProcessors.addProcessors(typedProcessorBuilder, zeebeState, stepProcessor);
  }

  private void addJobProcessors(
      ZeebeState zeebeState, TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    JobEventProcessors.addJobProcessors(typedProcessorBuilder, zeebeState);
  }

  private void addMessageProcessors(
      SubscriptionCommandSenderImpl subscriptionCommandSender,
      ZeebeState zeebeState,
      TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    MessageEventProcessors.addMessageProcessors(
        typedProcessorBuilder, zeebeState, subscriptionCommandSender);
  }

  @Override
  public ZbStreamProcessorService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public Injector<StreamProcessorServiceFactory> getStreamProcessorServiceFactoryInjector() {
    return streamProcessorServiceFactoryInjector;
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}

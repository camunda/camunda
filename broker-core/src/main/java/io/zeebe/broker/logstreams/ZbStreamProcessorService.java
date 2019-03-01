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

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.incident.processor.IncidentEventProcessors;
import io.zeebe.broker.job.JobEventProcessors;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory.Builder;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.state.DefaultZeebeDbFactory;
import io.zeebe.broker.logstreams.state.ZeebeState;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.processor.MessageEventProcessors;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.transport.controlmessage.ControlMessageHandlerManager;
import io.zeebe.broker.workflow.deployment.distribute.processor.DeploymentDistributeProcessor;
import io.zeebe.broker.workflow.processor.BpmnStepProcessor;
import io.zeebe.broker.workflow.processor.CatchEventBehavior;
import io.zeebe.broker.workflow.processor.WorkflowEventProcessors;
import io.zeebe.broker.workflow.processor.deployment.DeploymentCreatedProcessor;
import io.zeebe.broker.workflow.processor.deployment.DeploymentEventProcessors;
import io.zeebe.broker.workflow.processor.timer.DueDateTimerChecker;
import io.zeebe.broker.workflow.repository.WorkflowRepository;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.ServerTransport;

public class ZbStreamProcessorService implements Service<ZbStreamProcessorService> {

  public static final String PROCESSOR_NAME = "zb-stream-processor";

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<ClientTransport> managementApiClientInjector = new Injector<>();
  private final Injector<ClientTransport> subscriptionApiClientInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<ControlMessageHandlerManager> controlMessageHandlerManagerServiceInjector =
      new Injector<>();
  private final Injector<StreamProcessorServiceFactory> streamProcessorServiceFactoryInjector =
      new Injector<>();

  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((partitionName, partition) -> startStreamProcessors(partitionName, partition))
          .build();

  private final ClusterCfg clusterCfg;
  private ControlMessageHandlerManager controlMessageHandlerManager;

  public ZbStreamProcessorService(final ClusterCfg clusterCfg) {
    this.clusterCfg = clusterCfg;
  }

  private StreamProcessorServiceFactory streamProcessorServiceFactory;
  private ServerTransport clientApiTransport;
  private TopologyManager topologyManager;
  private ServiceStartContext startContext;
  private ClientTransport managementApi;

  @Override
  public void start(final ServiceStartContext serviceContext) {
    this.startContext = serviceContext;
    this.managementApi = managementApiClientInjector.getValue();
    this.clientApiTransport = clientApiTransportInjector.getValue();
    this.streamProcessorServiceFactory = streamProcessorServiceFactoryInjector.getValue();
    this.topologyManager = topologyManagerInjector.getValue();
    controlMessageHandlerManager = controlMessageHandlerManagerServiceInjector.getValue();
  }

  public void startStreamProcessors(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {
    final int partitionId = partition.getInfo().getPartitionId();

    final Builder streamProcessorServiceBuilder =
        streamProcessorServiceFactory
            .createService(partition, partitionServiceName)
            .processorId(partitionId)
            .processorName(PROCESSOR_NAME);

    final StateStorage stateStorage =
        partition.getStateStorageFactory().create(partitionId, PROCESSOR_NAME);
    final StateSnapshotController stateSnapshotController =
        new StateSnapshotController(DefaultZeebeDbFactory.DEFAULT_DB_FACTORY, stateStorage);

    streamProcessorServiceBuilder
        .snapshotController(stateSnapshotController)
        .streamProcessorFactory(
            (zeebeDb) -> {
              final ZeebeState zeebeState = new ZeebeState(partitionId, zeebeDb);
              final TypedStreamEnvironment streamEnvironment =
                  new TypedStreamEnvironment(
                      partition.getLogStream(), clientApiTransport.getOutput());

              return createTypedStreamProcessor(
                  partitionServiceName, partitionId, streamEnvironment, zeebeState);
            })
        .build();
  }

  public TypedStreamProcessor createTypedStreamProcessor(
      ServiceName<Partition> partitionServiceName,
      int partitionId,
      TypedStreamEnvironment streamEnvironment,
      ZeebeState zeebeState) {
    final TypedEventStreamProcessorBuilder typedProcessorBuilder =
        streamEnvironment.newStreamProcessor().zeebeState(zeebeState);

    addDistributeDeploymentProcessors(zeebeState, streamEnvironment, typedProcessorBuilder);
    final BpmnStepProcessor stepProcessor =
        addWorkflowProcessors(zeebeState, typedProcessorBuilder);
    addDeploymentRelatedProcessorAndServices(
        partitionServiceName, partitionId, zeebeState, typedProcessorBuilder);
    addIncidentProcessors(zeebeState, stepProcessor, typedProcessorBuilder);
    addJobProcessors(zeebeState, typedProcessorBuilder);
    addMessageProcessors(zeebeState, typedProcessorBuilder);

    return typedProcessorBuilder.build();
  }

  private void addDistributeDeploymentProcessors(
      ZeebeState zeebeState,
      TypedStreamEnvironment streamEnvironment,
      TypedEventStreamProcessorBuilder typedEventStreamProcessorBuilder) {
    final LogStream stream = streamEnvironment.getStream();
    final LogStreamWriterImpl logStreamWriter = new LogStreamWriterImpl(stream);

    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            clusterCfg,
            topologyManager,
            zeebeState.getDeploymentState(),
            managementApi,
            logStreamWriter);

    typedEventStreamProcessorBuilder.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);
  }

  private BpmnStepProcessor addWorkflowProcessors(
      ZeebeState zeebeState, TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    final SubscriptionCommandSender subscriptionCommandSender =
        new SubscriptionCommandSender(subscriptionApiClientInjector.getValue());
    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getWorkflowState());
    return WorkflowEventProcessors.addWorkflowProcessors(
        typedProcessorBuilder,
        zeebeState,
        subscriptionCommandSender,
        topologyManager,
        timerChecker,
        clusterCfg.getPartitionsCount());
  }

  public void addDeploymentRelatedProcessorAndServices(
      ServiceName<Partition> partitionServiceName,
      int partitionId,
      ZeebeState zeebeState,
      TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final boolean isDepoymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
    if (isDepoymentPartition) {
      typedProcessorBuilder.withListener(
          new WorkflowRepository(
              clientApiTransport,
              controlMessageHandlerManager,
              startContext,
              workflowState,
              partitionServiceName));
      DeploymentEventProcessors.addTransformingDeploymentProcessor(
          typedProcessorBuilder,
          zeebeState,
          new CatchEventBehavior(
              zeebeState,
              new SubscriptionCommandSender(managementApi),
              clusterCfg.getPartitionsCount()));
    } else {
      DeploymentEventProcessors.addDeploymentCreateProcessor(typedProcessorBuilder, workflowState);
    }

    typedProcessorBuilder.onEvent(
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED,
        new DeploymentCreatedProcessor(workflowState, isDepoymentPartition));
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
      ZeebeState zeebeState, TypedEventStreamProcessorBuilder typedProcessorBuilder) {
    final SubscriptionCommandSender subscriptionCommandSender =
        new SubscriptionCommandSender(getSubscriptionApiClientInjector().getValue());
    MessageEventProcessors.addMessageProcessors(
        typedProcessorBuilder, zeebeState, subscriptionCommandSender, topologyManager);
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

  public Injector<ClientTransport> getManagementApiClientInjector() {
    return managementApiClientInjector;
  }

  public Injector<ClientTransport> getSubscriptionApiClientInjector() {
    return subscriptionApiClientInjector;
  }

  public Injector<ControlMessageHandlerManager> getControlMessageHandlerManagerServiceInjector() {
    return controlMessageHandlerManagerServiceInjector;
  }
}

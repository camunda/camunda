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

import io.atomix.core.Atomix;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.clustering.base.topology.TopologyPartitionListenerImpl;
import io.zeebe.broker.engine.impl.DeploymentDistributorImpl;
import io.zeebe.broker.engine.impl.SubscriptionCommandSenderImpl;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.broker.system.configuration.DataCfg;
import io.zeebe.broker.transport.clientapi.CommandResponseWriterImpl;
import io.zeebe.engine.processor.ProcessingContext;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.TypedRecordProcessors;
import io.zeebe.engine.processor.workflow.BpmnStepProcessor;
import io.zeebe.engine.processor.workflow.CatchEventBehavior;
import io.zeebe.engine.processor.workflow.WorkflowEventProcessors;
import io.zeebe.engine.processor.workflow.deployment.DeploymentCreatedProcessor;
import io.zeebe.engine.processor.workflow.deployment.DeploymentEventProcessors;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributeProcessor;
import io.zeebe.engine.processor.workflow.incident.IncidentEventProcessors;
import io.zeebe.engine.processor.workflow.job.JobEventProcessors;
import io.zeebe.engine.processor.workflow.message.MessageEventProcessors;
import io.zeebe.engine.processor.workflow.timer.DueDateTimerChecker;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerTransport;
import io.zeebe.util.DurationUtil;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.ActorScheduler;
import java.time.Duration;

public class EngineService implements Service<EngineService> {

  public static final String PROCESSOR_NAME = "zb-stream-processor";

  private final Injector<ServerTransport> clientApiTransportInjector = new Injector<>();
  private final Injector<TopologyManager> topologyManagerInjector = new Injector<>();
  private final Injector<Atomix> atomixInjector = new Injector<>();

  private final ClusterCfg clusterCfg;
  private final ServiceContainer serviceContainer;
  private final Duration snapshotPeriod;
  private final int maxSnapshots;

  private ServerTransport clientApiTransport;
  private TopologyManager topologyManager;
  private Atomix atomix;
  private final ServiceGroupReference<Partition> partitionsGroupReference =
      ServiceGroupReference.<Partition>create().onAdd(this::startEngineForPartition).build();
  private ActorScheduler scheduler;

  public EngineService(ServiceContainer serviceContainer, BrokerCfg brokerCfg) {
    clusterCfg = brokerCfg.getCluster();

    this.serviceContainer = serviceContainer;
    final DataCfg dataCfg = brokerCfg.getData();
    this.snapshotPeriod = DurationUtil.parse(dataCfg.getSnapshotPeriod());
    this.maxSnapshots = dataCfg.getMaxSnapshots();
  }

  @Override
  public void start(final ServiceStartContext serviceContext) {
    this.scheduler = serviceContext.getScheduler();
    this.clientApiTransport = clientApiTransportInjector.getValue();
    this.topologyManager = topologyManagerInjector.getValue();
    this.atomix = atomixInjector.getValue();
  }

  public void startEngineForPartition(
      final ServiceName<Partition> partitionServiceName, final Partition partition) {
    final int partitionId = partition.getPartitionId();

    final LogStream logStream = partition.getLogStream();
    StreamProcessor.builder(partitionId, PROCESSOR_NAME)
        .logStream(logStream)
        .actorScheduler(scheduler)
        .additionalDependencies(partitionServiceName)
        .snapshotController(partition.getProcessorSnapshotController())
        .maxSnapshots(maxSnapshots)
        .snapshotPeriod(snapshotPeriod)
        .serviceContainer(serviceContainer)
        .commandResponseWriter(new CommandResponseWriterImpl(clientApiTransport.getOutput()))
        .streamProcessorFactory(
            (processingContext) -> {
              final ActorControl actor = processingContext.getActor();
              final ZeebeState zeebeState = processingContext.getZeebeState();
              return createTypedStreamProcessor(actor, partitionId, zeebeState, processingContext);
            })
        .deleteDataOnSnapshot(true)
        .build();
  }

  public TypedRecordProcessors createTypedStreamProcessor(
      ActorControl actor,
      int partitionId,
      ZeebeState zeebeState,
      ProcessingContext processingContext) {
    final TypedRecordProcessors typedRecordProcessors = TypedRecordProcessors.processors();
    final LogStream stream = processingContext.getLogStream();

    addDistributeDeploymentProcessors(actor, zeebeState, stream, typedRecordProcessors);

    final SubscriptionCommandSenderImpl subscriptionCommandSender =
        new SubscriptionCommandSenderImpl(atomix);
    subscriptionCommandSender.init(topologyManager, actor, stream);
    final CatchEventBehavior catchEventBehavior =
        new CatchEventBehavior(
            zeebeState, subscriptionCommandSender, clusterCfg.getPartitionsCount());

    addDeploymentRelatedProcessorAndServices(
        catchEventBehavior, partitionId, zeebeState, typedRecordProcessors);
    addMessageProcessors(subscriptionCommandSender, zeebeState, typedRecordProcessors);

    final BpmnStepProcessor stepProcessor =
        addWorkflowProcessors(
            zeebeState, typedRecordProcessors, subscriptionCommandSender, catchEventBehavior);
    addIncidentProcessors(zeebeState, stepProcessor, typedRecordProcessors);
    addJobProcessors(zeebeState, typedRecordProcessors);

    return typedRecordProcessors;
  }

  private void addDistributeDeploymentProcessors(
      ActorControl actor,
      ZeebeState zeebeState,
      LogStream stream,
      TypedRecordProcessors typedRecordProcessors) {

    final TopologyPartitionListenerImpl partitionListener =
        new TopologyPartitionListenerImpl(actor);
    topologyManager.addTopologyPartitionListener(partitionListener);

    final DeploymentDistributorImpl deploymentDistributor =
        new DeploymentDistributorImpl(
            clusterCfg, atomix, partitionListener, zeebeState.getDeploymentState(), actor);
    final DeploymentDistributeProcessor deploymentDistributeProcessor =
        new DeploymentDistributeProcessor(
            zeebeState.getDeploymentState(),
            new LogStreamWriterImpl(stream),
            deploymentDistributor);

    typedRecordProcessors.onCommand(
        ValueType.DEPLOYMENT, DeploymentIntent.DISTRIBUTE, deploymentDistributeProcessor);
  }

  private BpmnStepProcessor addWorkflowProcessors(
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors,
      SubscriptionCommandSenderImpl subscriptionCommandSender,
      CatchEventBehavior catchEventBehavior) {
    final DueDateTimerChecker timerChecker = new DueDateTimerChecker(zeebeState.getWorkflowState());
    return WorkflowEventProcessors.addWorkflowProcessors(
        zeebeState,
        typedRecordProcessors,
        subscriptionCommandSender,
        catchEventBehavior,
        timerChecker);
  }

  public void addDeploymentRelatedProcessorAndServices(
      CatchEventBehavior catchEventBehavior,
      int partitionId,
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors) {
    final WorkflowState workflowState = zeebeState.getWorkflowState();
    final boolean isDeploymentPartition = partitionId == Protocol.DEPLOYMENT_PARTITION;
    if (isDeploymentPartition) {
      DeploymentEventProcessors.addTransformingDeploymentProcessor(
          typedRecordProcessors, zeebeState, catchEventBehavior);
    } else {
      DeploymentEventProcessors.addDeploymentCreateProcessor(typedRecordProcessors, workflowState);
    }

    typedRecordProcessors.onEvent(
        ValueType.DEPLOYMENT,
        DeploymentIntent.CREATED,
        new DeploymentCreatedProcessor(workflowState, isDeploymentPartition));
  }

  private void addIncidentProcessors(
      ZeebeState zeebeState,
      BpmnStepProcessor stepProcessor,
      TypedRecordProcessors typedRecordProcessors) {
    IncidentEventProcessors.addProcessors(typedRecordProcessors, zeebeState, stepProcessor);
  }

  private void addJobProcessors(
      ZeebeState zeebeState, TypedRecordProcessors typedRecordProcessors) {
    JobEventProcessors.addJobProcessors(typedRecordProcessors, zeebeState);
  }

  private void addMessageProcessors(
      SubscriptionCommandSenderImpl subscriptionCommandSender,
      ZeebeState zeebeState,
      TypedRecordProcessors typedRecordProcessors) {
    MessageEventProcessors.addMessageProcessors(
        typedRecordProcessors, zeebeState, subscriptionCommandSender);
  }

  @Override
  public EngineService get() {
    return this;
  }

  public Injector<ServerTransport> getClientApiTransportInjector() {
    return clientApiTransportInjector;
  }

  public ServiceGroupReference<Partition> getPartitionsGroupReference() {
    return partitionsGroupReference;
  }

  public Injector<TopologyManager> getTopologyManagerInjector() {
    return topologyManagerInjector;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}

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

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.KeyGenerator;
import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.logstreams.processor.TypedEventStreamProcessorBuilder;
import io.zeebe.broker.logstreams.processor.TypedStreamEnvironment;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamReader;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.processor.OpenWorkflowInstanceSubscriptionProcessor;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.broker.workflow.processor.instance.CancelWorkflowInstanceProcessor;
import io.zeebe.broker.workflow.processor.instance.CreateWorkflowInstanceEventProcessor;
import io.zeebe.broker.workflow.processor.instance.UpdatePayloadProcessor;
import io.zeebe.broker.workflow.processor.instance.WorkflowInstanceCreatedEventProcessor;
import io.zeebe.broker.workflow.processor.instance.WorkflowInstanceRejectedEventProcessor;
import io.zeebe.broker.workflow.processor.job.JobCompletedEventProcessor;
import io.zeebe.broker.workflow.processor.job.JobCreatedProcessor;
import io.zeebe.broker.workflow.processor.message.CorrelateWorkflowInstanceSubscription;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;

public class WorkflowInstanceStreamProcessor implements StreamProcessorLifecycleAware {

  private final ElementInstanceIndex scopeInstances = new ElementInstanceIndex();

  private TypedStreamReader streamReader;
  private WorkflowInstanceSubscriptionDataStore subscriptionStore =
      new WorkflowInstanceSubscriptionDataStore();

  private final TopologyManager topologyManager;
  private final WorkflowCache workflowCache;
  private final SubscriptionCommandSender subscriptionCommandSender;

  public WorkflowInstanceStreamProcessor(
      WorkflowCache workflowCache,
      SubscriptionCommandSender subscriptionCommandSender,
      TopologyManager topologyManager) {
    this.workflowCache = workflowCache;
    this.subscriptionCommandSender = subscriptionCommandSender;
    this.topologyManager = topologyManager;
  }

  public TypedStreamProcessor createStreamProcessor(TypedStreamEnvironment environment) {

    final ComposeableSerializableSnapshot<ElementInstanceIndex> snapshotSupport =
        new ComposeableSerializableSnapshot<>(scopeInstances);

    final TypedEventStreamProcessorBuilder streamProcessorBuilder =
        environment
            .newStreamProcessor()
            .keyGenerator(KeyGenerator.createWorkflowInstanceKeyGenerator());

    addWorkflowInstanceEventStreamProcessors(streamProcessorBuilder);
    addBpmnStepProcessor(streamProcessorBuilder);
    addJobStreamProcessors(streamProcessorBuilder);
    addMessageStreamProcessors(streamProcessorBuilder);

    return streamProcessorBuilder
        // this is pretty ugly, but goes away when we switch to rocksdb
        .withStateResource(snapshotSupport)
        .withStateResource(subscriptionStore)
        .withListener(this)
        .withListener(
            new StreamProcessorLifecycleAware() {
              @Override
              public void onOpen(TypedStreamProcessor streamProcessor) {
                scopeInstances.shareState(snapshotSupport.getObject());
              }
            })
        .build();
  }

  public void addWorkflowInstanceEventStreamProcessors(TypedEventStreamProcessorBuilder builder) {
    builder
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CREATE,
            new CreateWorkflowInstanceEventProcessor(workflowCache))
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CREATED,
            new WorkflowInstanceCreatedEventProcessor(scopeInstances))
        .onRejection(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CREATE,
            new WorkflowInstanceRejectedEventProcessor())
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.CANCEL,
            new CancelWorkflowInstanceProcessor(scopeInstances))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.UPDATE_PAYLOAD,
            new UpdatePayloadProcessor(scopeInstances));
  }

  private void addBpmnStepProcessor(TypedEventStreamProcessorBuilder streamProcessorBuilder) {
    final BpmnStepProcessor bpmnStepProcessor =
        new BpmnStepProcessor(
            scopeInstances, workflowCache, subscriptionCommandSender, subscriptionStore);

    streamProcessorBuilder
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_READY, bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.END_EVENT_OCCURRED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.GATEWAY_ACTIVATED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_TERMINATING,
            bpmnStepProcessor)
        .onEvent(
            ValueType.WORKFLOW_INSTANCE,
            WorkflowInstanceIntent.ELEMENT_TERMINATED,
            bpmnStepProcessor);
  }

  private void addMessageStreamProcessors(TypedEventStreamProcessorBuilder streamProcessorBuilder) {
    streamProcessorBuilder
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.OPEN,
            new OpenWorkflowInstanceSubscriptionProcessor(subscriptionStore))
        .onCommand(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionIntent.CORRELATE,
            new CorrelateWorkflowInstanceSubscription(
                scopeInstances,
                subscriptionStore,
                topologyManager,
                workflowCache,
                subscriptionCommandSender));
  }

  private void addJobStreamProcessors(TypedEventStreamProcessorBuilder streamProcessorBuilder) {
    streamProcessorBuilder
        .onEvent(ValueType.JOB, JobIntent.CREATED, new JobCreatedProcessor(scopeInstances))
        .onEvent(
            ValueType.JOB, JobIntent.COMPLETED, new JobCompletedEventProcessor(scopeInstances));
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    this.streamReader = streamProcessor.getEnvironment().buildStreamReader();
  }

  @Override
  public void onClose() {
    workflowCache.close();
    streamReader.close();
  }
}

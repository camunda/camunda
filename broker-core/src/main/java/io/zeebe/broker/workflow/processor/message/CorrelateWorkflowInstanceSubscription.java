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
package io.zeebe.broker.workflow.processor.message;

import io.zeebe.broker.clustering.base.topology.TopologyManager;
import io.zeebe.broker.logstreams.processor.SideEffectProducer;
import io.zeebe.broker.logstreams.processor.TypedBatchWriter;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.subscription.message.processor.PendingWorkflowInstanceSubscriptionChecker;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionDataStore;
import io.zeebe.broker.workflow.data.WorkflowInstanceRecord;
import io.zeebe.broker.workflow.index.ElementInstance;
import io.zeebe.broker.workflow.index.ElementInstanceIndex;
import io.zeebe.broker.workflow.map.DeployedWorkflow;
import io.zeebe.broker.workflow.map.WorkflowCache;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.function.Consumer;

public final class CorrelateWorkflowInstanceSubscription
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final ElementInstanceIndex scopeInstances;
  private final WorkflowInstanceSubscriptionDataStore subscriptionStore;
  private final TopologyManager topologyManager;
  private final WorkflowCache workflowCache;
  private final SubscriptionCommandSender subscriptionCommandSender;

  private TypedRecord<WorkflowInstanceSubscriptionRecord> record;
  private WorkflowInstanceSubscriptionRecord subscription;
  private TypedStreamWriter streamWriter;
  private Consumer<SideEffectProducer> sideEffect;

  public CorrelateWorkflowInstanceSubscription(
      ElementInstanceIndex scopeInstances,
      WorkflowInstanceSubscriptionDataStore subscriptionStore,
      TopologyManager topologyManager,
      WorkflowCache workflowCache,
      SubscriptionCommandSender subscriptionCommandSender) {
    this.scopeInstances = scopeInstances;
    this.subscriptionStore = subscriptionStore;
    this.topologyManager = topologyManager;
    this.workflowCache = workflowCache;
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  @Override
  public void onOpen(TypedStreamProcessor streamProcessor) {
    final ActorControl actor = streamProcessor.getActor();
    final LogStream logStream = streamProcessor.getEnvironment().getStream();

    subscriptionCommandSender.init(topologyManager, actor, logStream);

    final PendingWorkflowInstanceSubscriptionChecker pendingSubscriptionChecker =
        new PendingWorkflowInstanceSubscriptionChecker(
            subscriptionCommandSender, subscriptionStore, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onClose() {}

  @Override
  public void processRecord(
      TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {

    this.record = record;
    this.subscription = record.getValue();
    this.streamWriter = streamWriter;
    this.sideEffect = sideEffect;

    final ElementInstance eventInstance =
        scopeInstances.getInstance(subscription.getActivityInstanceKey());

    if (eventInstance == null) {
      streamWriter.writeRejection(
          record, RejectionType.NOT_APPLICABLE, "activity is not active anymore");

    } else {
      final long workflowKey = eventInstance.getValue().getWorkflowKey();
      final DeployedWorkflow workflow = workflowCache.getWorkflowByKey(workflowKey);
      if (workflow != null) {
        onWorkflowAvailable();
      } else {
        streamWriter.writeRejection(
            record, RejectionType.NOT_APPLICABLE, "workflow is not available");
      }
    }
  }

  private void onWorkflowAvailable() {
    // remove subscription if pending
    final boolean removed = subscriptionStore.removeSubscription(subscription);
    if (!removed) {
      streamWriter.writeRejection(
          record, RejectionType.NOT_APPLICABLE, "subscription is already correlated");

      sideEffect.accept(this::sendAcknowledgeCommand);
      return;
    }

    final ElementInstance eventInstance =
        scopeInstances.getInstance(subscription.getActivityInstanceKey());

    final WorkflowInstanceRecord value = eventInstance.getValue();
    value.setPayload(subscription.getPayload());

    final TypedBatchWriter batchWriter = streamWriter.newBatch();
    batchWriter.addFollowUpEvent(
        record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscription);
    batchWriter.addFollowUpEvent(
        subscription.getActivityInstanceKey(), WorkflowInstanceIntent.ELEMENT_COMPLETING, value);

    sideEffect.accept(this::sendAcknowledgeCommand);

    eventInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    eventInstance.setValue(value);
  }

  private boolean sendAcknowledgeCommand() {
    return subscriptionCommandSender.correlateMessageSubscription(
        subscription.getSubscriptionPartitionId(),
        subscription.getWorkflowInstanceKey(),
        subscription.getActivityInstanceKey(),
        subscription.getMessageName());
  }
}

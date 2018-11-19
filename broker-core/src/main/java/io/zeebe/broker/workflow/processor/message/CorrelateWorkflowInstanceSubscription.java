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
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.logstreams.processor.TypedRecordProcessor;
import io.zeebe.broker.logstreams.processor.TypedResponseWriter;
import io.zeebe.broker.logstreams.processor.TypedStreamProcessor;
import io.zeebe.broker.logstreams.processor.TypedStreamWriter;
import io.zeebe.broker.subscription.command.SubscriptionCommandSender;
import io.zeebe.broker.subscription.message.data.WorkflowInstanceSubscriptionRecord;
import io.zeebe.broker.subscription.message.state.WorkflowInstanceSubscriptionState;
import io.zeebe.broker.workflow.processor.boundary.BoundaryEventHelper;
import io.zeebe.broker.workflow.state.DeployedWorkflow;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.WorkflowInstanceSubscription;
import io.zeebe.broker.workflow.state.WorkflowState;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.function.Consumer;

public final class CorrelateWorkflowInstanceSubscription
    implements TypedRecordProcessor<WorkflowInstanceSubscriptionRecord> {

  public static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(10);
  public static final Duration SUBSCRIPTION_CHECK_INTERVAL = Duration.ofSeconds(30);

  private final BoundaryEventHelper boundaryEventHelper = new BoundaryEventHelper();
  private final TopologyManager topologyManager;
  private final WorkflowState workflowState;
  private final WorkflowInstanceSubscriptionState subscriptionState;
  private final SubscriptionCommandSender subscriptionCommandSender;

  private TypedRecord<WorkflowInstanceSubscriptionRecord> record;
  private WorkflowInstanceSubscriptionRecord subscriptionRecord;
  private TypedStreamWriter streamWriter;
  private Consumer<SideEffectProducer> sideEffect;

  public CorrelateWorkflowInstanceSubscription(
      final TopologyManager topologyManager,
      final WorkflowState workflowState,
      final WorkflowInstanceSubscriptionState subscriptionState,
      final SubscriptionCommandSender subscriptionCommandSender) {
    this.topologyManager = topologyManager;
    this.workflowState = workflowState;
    this.subscriptionState = subscriptionState;
    this.subscriptionCommandSender = subscriptionCommandSender;
  }

  @Override
  public void onOpen(final TypedStreamProcessor streamProcessor) {
    final ActorControl actor = streamProcessor.getActor();
    final LogStream logStream = streamProcessor.getEnvironment().getStream();

    subscriptionCommandSender.init(topologyManager, actor, logStream);

    final PendingWorkflowInstanceSubscriptionChecker pendingSubscriptionChecker =
        new PendingWorkflowInstanceSubscriptionChecker(
            subscriptionCommandSender, subscriptionState, SUBSCRIPTION_TIMEOUT.toMillis());
    actor.runAtFixedRate(SUBSCRIPTION_CHECK_INTERVAL, pendingSubscriptionChecker);
  }

  @Override
  public void onClose() {}

  @Override
  public void processRecord(
      final TypedRecord<WorkflowInstanceSubscriptionRecord> record,
      final TypedResponseWriter responseWriter,
      final TypedStreamWriter streamWriter,
      final Consumer<SideEffectProducer> sideEffect) {

    this.record = record;
    this.subscriptionRecord = record.getValue();
    this.streamWriter = streamWriter;
    this.sideEffect = sideEffect;

    final ElementInstance eventInstance =
        workflowState
            .getElementInstanceState()
            .getInstance(subscriptionRecord.getElementInstanceKey());

    if (eventInstance == null) {
      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "activity is not active anymore");

    } else {
      final long workflowKey = eventInstance.getValue().getWorkflowKey();
      final DeployedWorkflow workflow = workflowState.getWorkflowByKey(workflowKey);
      if (workflow != null) {
        onWorkflowAvailable();
      } else {
        streamWriter.appendRejection(
            record, RejectionType.NOT_APPLICABLE, "workflow is not available");
      }
    }
  }

  private void onWorkflowAvailable() {
    // remove subscription if pending
    final WorkflowInstanceSubscription subscription =
        subscriptionState.getSubscription(
            subscriptionRecord.getElementInstanceKey(), subscriptionRecord.getMessageName());
    if (subscription == null) {
      streamWriter.appendRejection(
          record, RejectionType.NOT_APPLICABLE, "subscription is already correlated");

      sideEffect.accept(this::sendAcknowledgeCommand);
      return;
    }

    subscriptionState.remove(subscription);

    final ElementInstance eventInstance =
        workflowState
            .getElementInstanceState()
            .getInstance(subscriptionRecord.getElementInstanceKey());

    final WorkflowInstanceRecord value = eventInstance.getValue();
    value.setPayload(subscriptionRecord.getPayload());

    streamWriter.appendFollowUpEvent(
        record.getKey(), WorkflowInstanceSubscriptionIntent.CORRELATED, subscriptionRecord);

    if (eventInstance.getState() == WorkflowInstanceIntent.ELEMENT_ACTIVATED) {
      if (boundaryEventHelper.shouldTriggerBoundaryEvent(
          eventInstance, subscription.getHandlerNodeId())) {
        boundaryEventHelper.triggerBoundaryEvent(
            workflowState,
            eventInstance,
            subscription.getHandlerNodeId(),
            subscriptionRecord.getPayload(),
            streamWriter);
      } else {
        streamWriter.appendFollowUpEvent(
            subscriptionRecord.getElementInstanceKey(),
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            value);
        eventInstance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
        eventInstance.setValue(value);
      }
    }

    sideEffect.accept(this::sendAcknowledgeCommand);
    workflowState.getElementInstanceState().flushDirtyState();
  }

  private boolean sendAcknowledgeCommand() {
    return subscriptionCommandSender.correlateMessageSubscription(
        subscriptionRecord.getSubscriptionPartitionId(),
        subscriptionRecord.getWorkflowInstanceKey(),
        subscriptionRecord.getElementInstanceKey(),
        subscriptionRecord.getMessageName());
  }
}

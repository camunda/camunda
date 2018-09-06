/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.impl.subscription.topic;

import io.zeebe.gateway.api.commands.IncidentCommand;
import io.zeebe.gateway.api.commands.JobCommand;
import io.zeebe.gateway.api.commands.WorkflowInstanceCommand;
import io.zeebe.gateway.api.events.IncidentEvent;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.RaftEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.record.RecordType;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.gateway.api.subscription.IncidentCommandHandler;
import io.zeebe.gateway.api.subscription.IncidentEventHandler;
import io.zeebe.gateway.api.subscription.JobCommandHandler;
import io.zeebe.gateway.api.subscription.JobEventHandler;
import io.zeebe.gateway.api.subscription.RaftEventHandler;
import io.zeebe.gateway.api.subscription.RecordHandler;
import io.zeebe.gateway.api.subscription.TopicSubscription;
import io.zeebe.gateway.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.gateway.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep2;
import io.zeebe.gateway.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3;
import io.zeebe.gateway.api.subscription.WorkflowInstanceCommandHandler;
import io.zeebe.gateway.api.subscription.WorkflowInstanceEventHandler;
import io.zeebe.gateway.cmd.ClientException;
import io.zeebe.gateway.impl.ZeebeClientImpl;
import io.zeebe.gateway.impl.record.RecordImpl;
import io.zeebe.gateway.impl.subscription.SubscriptionManager;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.EnsureUtil;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.agrona.collections.Long2LongHashMap;

public class TopicSubscriptionBuilderImpl
    implements TopicSubscriptionBuilderStep1,
        TopicSubscriptionBuilderStep2,
        TopicSubscriptionBuilderStep3 {
  private RecordHandler defaultRecordHandler;

  private final BiEnumMap<RecordType, ValueType, CheckedConsumer<RecordImpl>> handlers =
      new BiEnumMap<>(RecordType.class, ValueType.class, CheckedConsumer.class);

  private int bufferSize;
  private final SubscriptionManager subscriptionManager;
  private String name;
  private boolean forceStart;
  private long defaultStartPosition;
  private final Long2LongHashMap startPositions = new Long2LongHashMap(-1);

  public TopicSubscriptionBuilderImpl(final ZeebeClientImpl client) {
    this.subscriptionManager = client.getSubscriptionManager();
    this.bufferSize = client.getConfiguration().getDefaultTopicSubscriptionBufferSize();
  }

  @Override
  public TopicSubscriptionBuilderStep3 recordHandler(final RecordHandler handler) {
    EnsureUtil.ensureNotNull("recordHandler", handler);
    this.defaultRecordHandler = handler;
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 jobEventHandler(final JobEventHandler handler) {
    EnsureUtil.ensureNotNull("jobEventHandler", handler);
    handlers.put(RecordType.EVENT, ValueType.JOB, e -> handler.onJobEvent((JobEvent) e));

    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 jobCommandHandler(final JobCommandHandler handler) {
    EnsureUtil.ensureNotNull("jobCommandHandler", handler);
    handlers.put(RecordType.COMMAND, ValueType.JOB, e -> handler.onJobCommand((JobCommand) e));
    handlers.put(
        RecordType.COMMAND_REJECTION,
        ValueType.JOB,
        e -> handler.onJobCommandRejection((JobCommand) e));

    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 workflowInstanceEventHandler(
      final WorkflowInstanceEventHandler handler) {
    EnsureUtil.ensureNotNull("workflowInstanceEventHandler", handler);
    handlers.put(
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        e -> handler.onWorkflowInstanceEvent((WorkflowInstanceEvent) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 workflowInstanceCommandHandler(
      final WorkflowInstanceCommandHandler handler) {
    EnsureUtil.ensureNotNull("workflowInstanceCommandHandler", handler);
    handlers.put(
        RecordType.COMMAND,
        ValueType.WORKFLOW_INSTANCE,
        e -> handler.onWorkflowInstanceCommand((WorkflowInstanceCommand) e));
    handlers.put(
        RecordType.COMMAND_REJECTION,
        ValueType.WORKFLOW_INSTANCE,
        e -> handler.onWorkflowInstanceCommand((WorkflowInstanceCommand) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 incidentEventHandler(final IncidentEventHandler handler) {
    EnsureUtil.ensureNotNull("incidentEventHandler", handler);
    handlers.put(
        RecordType.EVENT, ValueType.INCIDENT, e -> handler.onIncidentEvent((IncidentEvent) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 incidentCommandHandler(
      final IncidentCommandHandler handler) {
    EnsureUtil.ensureNotNull("incidentCommandHandler", handler);
    handlers.put(
        RecordType.COMMAND,
        ValueType.INCIDENT,
        e -> handler.onIncidentCommand((IncidentCommand) e));
    handlers.put(
        RecordType.COMMAND_REJECTION,
        ValueType.INCIDENT,
        e -> handler.onIncidentCommand((IncidentCommand) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler handler) {
    EnsureUtil.ensureNotNull("raftEventHandler", handler);
    handlers.put(RecordType.EVENT, ValueType.RAFT, e -> handler.onRaftEvent((RaftEvent) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 startAtPosition(final int partitionId, final long position) {
    this.startPositions.put(partitionId, position);
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 startAtTail() {
    return defaultStartPosition(-1L);
  }

  @Override
  public TopicSubscriptionBuilderStep3 startAtHead() {
    return defaultStartPosition(0L);
  }

  private TopicSubscriptionBuilderImpl defaultStartPosition(final long position) {
    this.defaultStartPosition = position;
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 name(final String name) {
    EnsureUtil.ensureNotNull("name", name);
    this.name = name;
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 forcedStart() {
    this.forceStart = true;
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 bufferSize(final int bufferSize) {
    EnsureUtil.ensureGreaterThan("bufferSize", bufferSize, 0);

    this.bufferSize = bufferSize;
    return this;
  }

  @Override
  public TopicSubscription open() {
    final Future<TopicSubscriberGroup> subscription = buildSubscriberGroup();

    try {
      return subscription.get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new ClientException("Could not open subscriber group", e);
    }
  }

  public Future<TopicSubscriberGroup> buildSubscriberGroup() {
    final TopicSubscriptionSpec subscription =
        new TopicSubscriptionSpec(
            defaultStartPosition,
            startPositions,
            forceStart,
            name,
            bufferSize,
            handlers,
            defaultRecordHandler);

    return subscriptionManager.openTopicSubscription(subscription);
  }
}

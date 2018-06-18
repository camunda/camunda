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
package io.zeebe.client.impl.subscription.topic;

import io.zeebe.client.api.commands.*;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.RecordType;
import io.zeebe.client.api.record.ValueType;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep2;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.TopicClientImpl;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.client.impl.subscription.SubscriptionManager;
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

  private BiEnumMap<RecordType, ValueType, CheckedConsumer<RecordImpl>> handlers =
      new BiEnumMap<>(RecordType.class, ValueType.class, CheckedConsumer.class);

  private int bufferSize;
  private final String topic;
  private final SubscriptionManager subscriptionManager;
  private String name;
  private boolean forceStart;
  private long defaultStartPosition;
  private final Long2LongHashMap startPositions = new Long2LongHashMap(-1);

  public TopicSubscriptionBuilderImpl(TopicClientImpl client) {
    this.topic = client.getTopic();
    this.subscriptionManager = client.getSubscriptionManager();
    this.bufferSize = client.getConfiguration().getDefaultTopicSubscriptionBufferSize();
  }

  @Override
  public TopicSubscriptionBuilderStep3 recordHandler(RecordHandler handler) {
    EnsureUtil.ensureNotNull("recordHandler", handler);
    this.defaultRecordHandler = handler;
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 jobEventHandler(JobEventHandler handler) {
    EnsureUtil.ensureNotNull("jobEventHandler", handler);
    handlers.put(RecordType.EVENT, ValueType.JOB, e -> handler.onJobEvent((JobEvent) e));

    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 jobCommandHandler(JobCommandHandler handler) {
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
      WorkflowInstanceEventHandler handler) {
    EnsureUtil.ensureNotNull("workflowInstanceEventHandler", handler);
    handlers.put(
        RecordType.EVENT,
        ValueType.WORKFLOW_INSTANCE,
        e -> handler.onWorkflowInstanceEvent((WorkflowInstanceEvent) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 workflowInstanceCommandHandler(
      WorkflowInstanceCommandHandler handler) {
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
  public TopicSubscriptionBuilderStep3 incidentEventHandler(IncidentEventHandler handler) {
    EnsureUtil.ensureNotNull("incidentEventHandler", handler);
    handlers.put(
        RecordType.EVENT, ValueType.INCIDENT, e -> handler.onIncidentEvent((IncidentEvent) e));
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 incidentCommandHandler(IncidentCommandHandler handler) {
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
  public TopicSubscriptionBuilderStep3 startAtPosition(int partitionId, long position) {
    this.startPositions.put(partitionId, position);
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 startAtTailOfTopic() {
    return defaultStartPosition(-1L);
  }

  @Override
  public TopicSubscriptionBuilderStep3 startAtHeadOfTopic() {
    return defaultStartPosition(0L);
  }

  private TopicSubscriptionBuilderImpl defaultStartPosition(long position) {
    this.defaultStartPosition = position;
    return this;
  }

  @Override
  public TopicSubscriptionBuilderStep3 name(String name) {
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
  public TopicSubscriptionBuilderStep3 bufferSize(int bufferSize) {
    EnsureUtil.ensureGreaterThan("bufferSize", bufferSize, 0);

    this.bufferSize = bufferSize;
    return this;
  }

  @Override
  public TopicSubscription open() {
    final Future<TopicSubscriberGroup> subscription = buildSubscriberGroup();

    try {
      return subscription.get();
    } catch (InterruptedException | ExecutionException e) {
      throw new ClientException("Could not open subscriber group", e);
    }
  }

  public Future<TopicSubscriberGroup> buildSubscriberGroup() {
    final TopicSubscriptionSpec subscription =
        new TopicSubscriptionSpec(
            topic,
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

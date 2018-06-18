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

import io.zeebe.client.ZeebeClientConfiguration;
import io.zeebe.client.api.commands.DeploymentCommand;
import io.zeebe.client.api.commands.TopicCommand;
import io.zeebe.client.api.events.*;
import io.zeebe.client.api.record.RecordType;
import io.zeebe.client.api.record.ValueType;
import io.zeebe.client.api.subscription.*;
import io.zeebe.client.api.subscription.ManagementSubscriptionBuilderStep1.ManagementSubscriptionBuilderStep2;
import io.zeebe.client.api.subscription.ManagementSubscriptionBuilderStep1.ManagementSubscriptionBuilderStep3;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.client.impl.record.RecordImpl;
import io.zeebe.client.impl.subscription.SubscriptionManager;
import io.zeebe.protocol.Protocol;
import io.zeebe.util.CheckedConsumer;
import io.zeebe.util.EnsureUtil;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.agrona.collections.Long2LongHashMap;

public class ManagementSubscriptionBuilderImpl
    implements ManagementSubscriptionBuilderStep1,
        ManagementSubscriptionBuilderStep2,
        ManagementSubscriptionBuilderStep3 {
  private RecordHandler defaultRecordHandler;

  private BiEnumMap<RecordType, ValueType, CheckedConsumer<RecordImpl>> handlers =
      new BiEnumMap<>(RecordType.class, ValueType.class, CheckedConsumer.class);

  private int bufferSize;
  private final SubscriptionManager subscriptionManager;
  private String name;
  private boolean forceStart;
  private long defaultStartPosition;
  private final Long2LongHashMap startPositions = new Long2LongHashMap(-1);

  public ManagementSubscriptionBuilderImpl(
      SubscriptionManager subscriptionManager, ZeebeClientConfiguration configuration) {
    this.subscriptionManager = subscriptionManager;
    this.bufferSize = configuration.getDefaultTopicSubscriptionBufferSize();
  }

  @Override
  public ManagementSubscriptionBuilderStep3 recordHandler(RecordHandler handler) {
    EnsureUtil.ensureNotNull("recordHandler", handler);
    this.defaultRecordHandler = handler;
    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 deploymentEventHandler(DeploymentEventHandler handler) {
    EnsureUtil.ensureNotNull("deploymentEventHandler", handler);
    handlers.put(
        RecordType.EVENT,
        ValueType.DEPLOYMENT,
        e -> handler.onDeploymentEvent((DeploymentEvent) e));

    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 deploymentCommandHandler(
      DeploymentCommandHandler handler) {
    EnsureUtil.ensureNotNull("deploymentCommandHandler", handler);
    handlers.put(
        RecordType.COMMAND,
        ValueType.DEPLOYMENT,
        e -> handler.onDeploymentCommand((DeploymentCommand) e));
    handlers.put(
        RecordType.COMMAND_REJECTION,
        ValueType.DEPLOYMENT,
        e -> handler.onDeploymentCommandRejection((DeploymentCommand) e));

    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 topicEventHandler(TopicEventHandler handler) {
    EnsureUtil.ensureNotNull("topicEventHandler", handler);
    handlers.put(RecordType.EVENT, ValueType.TOPIC, e -> handler.onTopicEvent((TopicEvent) e));

    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 topicCommandHandler(TopicCommandHandler handler) {
    EnsureUtil.ensureNotNull("topicCommandHandler", handler);
    handlers.put(
        RecordType.COMMAND, ValueType.TOPIC, e -> handler.onTopicCommand((TopicCommand) e));
    handlers.put(
        RecordType.COMMAND_REJECTION,
        ValueType.TOPIC,
        e -> handler.onTopicCommandRejection((TopicCommand) e));

    return this;
  }

  @Override
  public ManagementSubscriptionBuilderImpl raftEventHandler(final RaftEventHandler handler) {
    EnsureUtil.ensureNotNull("raftEventHandler", handler);
    handlers.put(RecordType.EVENT, ValueType.RAFT, e -> handler.onRaftEvent((RaftEvent) e));
    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 startAtPosition(long position) {
    this.startPositions.put(Protocol.SYSTEM_PARTITION, position);
    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 startAtTailOfTopic() {
    return defaultStartPosition(-1L);
  }

  @Override
  public ManagementSubscriptionBuilderStep3 startAtHeadOfTopic() {
    return defaultStartPosition(0L);
  }

  private ManagementSubscriptionBuilderImpl defaultStartPosition(long position) {
    this.defaultStartPosition = position;
    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 name(String name) {
    EnsureUtil.ensureNotNull("name", name);
    this.name = name;
    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 forcedStart() {
    this.forceStart = true;
    return this;
  }

  @Override
  public ManagementSubscriptionBuilderStep3 bufferSize(int bufferSize) {
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
            Protocol.SYSTEM_TOPIC,
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

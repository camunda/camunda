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
package io.zeebe.broker.clustering.orchestration.state;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.broker.logstreams.processor.StreamProcessorLifecycleAware;
import io.zeebe.broker.system.configuration.ClusterCfg;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.value.ArrayValue;
import io.zeebe.msgpack.value.IntegerValue;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class KnownTopics extends Actor
    implements Service<KnownTopics>, StreamProcessorLifecycleAware {
  private final List<KnownTopicsListener> topicsListeners = new ArrayList<>();

  private final ArrayValue<TopicInfo> knownTopics = new ArrayValue<>(new TopicInfo());

  public KnownTopics(final ClusterCfg clusterCfg) {
    initKnownTopics(clusterCfg);
  }

  private void initKnownTopics(final ClusterCfg clusterCfg) {
    final TopicInfo topicInfo = knownTopics.add();

    topicInfo
        .setTopicName(wrapString(Protocol.SYSTEM_TOPIC))
        .setPartitionCount(clusterCfg.getPartitionsCount())
        .setReplicationFactor(clusterCfg.getReplicationFactor())
        .setKey(-1);
    final ArrayProperty<IntegerValue> partitionIds = topicInfo.partitionIds;
    partitionIds.reset();
    clusterCfg.getPartitionIds().forEach(id -> partitionIds.add().setValue(id));
  }

  @Override
  public KnownTopics get() {
    return this;
  }

  public void registerTopicListener(final KnownTopicsListener topicsListener) {
    actor.run(() -> topicsListeners.add(topicsListener));
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    startContext.getScheduler().submitActor(this);
  }

  public <R> ActorFuture<R> queryTopics(final Function<Iterable<TopicInfo>, R> query) {
    return actor.call(() -> query.apply(knownTopics));
  }
}

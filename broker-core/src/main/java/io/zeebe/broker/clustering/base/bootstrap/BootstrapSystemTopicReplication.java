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
package io.zeebe.broker.clustering.base.bootstrap;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.topology.PartitionInfo;
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.TopicIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.Actor;

/**
 * This service only writes a create topic event to the system topic, which then is picked up by the
 * corresponding services to ensure the replication factor.
 */
class BootstrapSystemTopicReplication extends Actor implements Service<Void> {

  private final Injector<Partition> partitionInjector = new Injector<>();

  private final RecordMetadata metadata = new RecordMetadata();
  private final TopicRecord topicEvent = new TopicRecord();
  private final LogStreamWriterImpl writer = new LogStreamWriterImpl();

  @Override
  public Void get() {
    return null;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  protected void onActorStarted() {
    final Partition partition = partitionInjector.getValue();
    final PartitionInfo partitionInfo = partition.getInfo();

    metadata.recordType(RecordType.EVENT);
    metadata.valueType(ValueType.TOPIC);
    metadata.intent(TopicIntent.CREATE_COMPLETE);

    topicEvent.setName(partitionInfo.getTopicNameBuffer());
    topicEvent.setReplicationFactor(partitionInfo.getReplicationFactor());
    topicEvent.setPartitions(1);
    topicEvent.getPartitionIds().add().setValue(Protocol.SYSTEM_PARTITION);

    writer.wrap(partition.getLogStream());

    actor.runUntilDone(this::writeEvent);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  private void writeEvent() {
    final long position =
        writer.positionAsKey().metadataWriter(metadata).valueWriter(topicEvent).tryWrite();

    if (position < 0) {
      actor.yield();
    } else {
      actor.done();
    }
  }

  public Injector<Partition> getPartitionInjector() {
    return partitionInjector;
  }
}

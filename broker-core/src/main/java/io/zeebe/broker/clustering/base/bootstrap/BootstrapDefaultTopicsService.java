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
import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.broker.system.configuration.TopicCfg;
import io.zeebe.logstreams.log.LogStreamBatchWriter;
import io.zeebe.logstreams.log.LogStreamBatchWriterImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.protocol.intent.TopicIntent;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.Actor;
import java.util.List;

public class BootstrapDefaultTopicsService extends Actor implements Service<Void> {
  private final Injector<Partition> partitionInjector = new Injector<>();
  private final RecordMetadata metadata = new RecordMetadata();
  private final LogStreamBatchWriter writer = new LogStreamBatchWriterImpl();
  private final List<TopicCfg> topics;

  public BootstrapDefaultTopicsService(List<TopicCfg> topics) {
    this.topics = topics;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(actor.close());
  }

  @Override
  protected void onActorStarted() {
    final Partition partition = partitionInjector.getValue();

    metadata.recordType(RecordType.COMMAND);
    metadata.valueType(ValueType.TOPIC);
    metadata.intent(TopicIntent.CREATE);

    writer.wrap(partition.getLogStream());
    actor.runUntilDone(this::writeTopicEvents);
  }

  @Override
  public Void get() {
    return null;
  }

  Injector<Partition> getPartitionInjector() {
    return partitionInjector;
  }

  private void writeTopicEvents() {
    topics.forEach(this::writeTopicEvent);
    final long position = writer.tryWrite();

    if (position < 0) {
      actor.yield();
    } else {
      actor.done();
    }
  }

  private void writeTopicEvent(TopicCfg config) {
    final TopicRecord record = recordFromConfig(config);
    writer.event().positionAsKey().metadataWriter(metadata).valueWriter(record).done();
  }

  private TopicRecord recordFromConfig(TopicCfg config) {
    final TopicRecord topic = new TopicRecord();
    topic.setName(BufferUtil.wrapString(config.getName()));
    topic.setPartitions(config.getPartitions());
    topic.setReplicationFactor(config.getReplicationFactor());

    return topic;
  }
}

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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.zeebe.broker.clustering.orchestration.topic.TopicRecord;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.msgpack.value.IntegerValue;
import io.zeebe.msgpack.value.ValueArray;
import org.agrona.DirectBuffer;

public class TopicInfo extends UnpackedObject {

  protected final StringProperty topicName = new StringProperty("topicName");
  protected final IntegerProperty partitionCount = new IntegerProperty("partitionCount");
  protected final IntegerProperty replicationFactor = new IntegerProperty("replicationFactor");
  protected final LongProperty key = new LongProperty("key");
  protected final ArrayProperty<IntegerValue> partitionIds =
      new ArrayProperty<>("partitionIds", new IntegerValue());

  public TopicInfo() {
    this.declareProperty(topicName)
        .declareProperty(partitionCount)
        .declareProperty(replicationFactor)
        .declareProperty(key)
        .declareProperty(partitionIds);
  }

  public String getTopicName() {
    return bufferAsString(getTopicNameBuffer());
  }

  public DirectBuffer getTopicNameBuffer() {
    return topicName.getValue();
  }

  public TopicInfo setTopicName(final DirectBuffer topicName) {
    this.topicName.setValue(cloneBuffer(topicName));
    return this;
  }

  public int getPartitionCount() {
    return partitionCount.getValue();
  }

  public TopicInfo setPartitionCount(final int partitionCount) {
    this.partitionCount.setValue(partitionCount);
    return this;
  }

  public int getReplicationFactor() {
    return replicationFactor.getValue();
  }

  public TopicInfo setReplicationFactor(final int replicationFactor) {
    this.replicationFactor.setValue(replicationFactor);
    return this;
  }

  public long getKey() {
    return key.getValue();
  }

  public TopicInfo setKey(final long key) {
    this.key.setValue(key);
    return this;
  }

  public ValueArray<IntegerValue> getPartitionIds() {
    return partitionIds;
  }

  public void asTopicEvent(final TopicRecord topicEvent) {
    topicEvent.reset();
    topicEvent.setName(getTopicNameBuffer());
    topicEvent.setPartitions(getPartitionCount());
    topicEvent.setReplicationFactor(getReplicationFactor());

    final ValueArray<IntegerValue> eventPartitionIds = topicEvent.getPartitionIds();
    getPartitionIds().forEach(id -> eventPartitionIds.add().setValue(id.getValue()));
  }

  public TopicRecord toTopicEvent() {
    final TopicRecord topicEvent = new TopicRecord();
    asTopicEvent(topicEvent);
    return topicEvent;
  }
}

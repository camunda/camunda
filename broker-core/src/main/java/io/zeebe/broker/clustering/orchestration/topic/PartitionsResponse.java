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
package io.zeebe.broker.clustering.orchestration.topic;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

public class PartitionsResponse extends UnpackedObject {
  protected ArrayProperty<Partition> partitions =
      new ArrayProperty<>("partitions", new Partition());
  protected final MutableDirectBuffer topicName = new ExpandableArrayBuffer(128);

  public PartitionsResponse() {
    declareProperty(partitions);
  }

  public void addPartition(int id, DirectBuffer topic) {
    final Partition partition = partitions.add();

    topicName.putBytes(0, topic, 0, topic.capacity());
    // copy the topic name because arrayproperty#add does not
    // copy the value immediately (only on the next add() or write() invocation),
    // and the buffer's content may change until then

    partition.setId(id);
    partition.setTopic(topicName, 0, topic.capacity());
  }

  protected static class Partition extends UnpackedObject {
    protected IntegerProperty idProperty = new IntegerProperty("id");
    protected StringProperty topicProperty = new StringProperty("topic");

    public Partition() {
      declareProperty(idProperty).declareProperty(topicProperty);
    }

    public void setId(int id) {
      this.idProperty.setValue(id);
    }

    public void setTopic(DirectBuffer topic, int offset, int length) {
      this.topicProperty.setValue(topic, offset, length);
    }
  }
}

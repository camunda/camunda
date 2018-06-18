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
package io.zeebe.broker.clustering.api;

import io.zeebe.clustering.management.CreatePartitionRequestDecoder;
import io.zeebe.clustering.management.CreatePartitionRequestEncoder;
import io.zeebe.clustering.management.MessageHeaderDecoder;
import io.zeebe.clustering.management.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class CreatePartitionRequest implements BufferReader, BufferWriter {
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final CreatePartitionRequestEncoder bodyEncoder = new CreatePartitionRequestEncoder();

  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final CreatePartitionRequestDecoder bodyDecoder = new CreatePartitionRequestDecoder();

  protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
  protected int partitionId = CreatePartitionRequestEncoder.partitionIdNullValue();
  protected int replicationFactor = CreatePartitionRequestEncoder.replicationFactorNullValue();

  @Override
  public int getLength() {
    return headerEncoder.encodedLength()
        + bodyEncoder.sbeBlockLength()
        + CreatePartitionRequestEncoder.topicNameHeaderLength()
        + topicName.capacity();
  }

  public CreatePartitionRequest topicName(DirectBuffer topicName) {
    this.topicName.wrap(topicName);
    return this;
  }

  public CreatePartitionRequest partitionId(int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public CreatePartitionRequest replicationFactor(int replicationFactor) {
    this.replicationFactor = replicationFactor;
    return this;
  }

  public DirectBuffer getTopicName() {
    return topicName;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    bodyEncoder
        .wrap(buffer, offset + headerEncoder.encodedLength())
        .partitionId(partitionId)
        .replicationFactor(replicationFactor)
        .putTopicName(topicName, 0, topicName.capacity());
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    partitionId = bodyDecoder.partitionId();
    replicationFactor = bodyDecoder.replicationFactor();

    offset += headerDecoder.blockLength();

    final int topicNameLength = bodyDecoder.topicNameLength();
    offset += CreatePartitionRequestDecoder.topicNameHeaderLength();

    topicName.wrap(buffer, offset, topicNameLength);
  }
}

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
package io.zeebe.broker.system.management.topics;

import io.zeebe.broker.util.SbeBufferWriterReader;
import io.zeebe.clustering.management.FetchCreatedTopicsResponseDecoder;
import io.zeebe.clustering.management.FetchCreatedTopicsResponseDecoder.TopicsDecoder;
import io.zeebe.clustering.management.FetchCreatedTopicsResponseEncoder;
import io.zeebe.clustering.management.FetchCreatedTopicsResponseEncoder.TopicsEncoder;
import io.zeebe.clustering.management.FetchCreatedTopicsResponseEncoder.TopicsEncoder.PartitionsEncoder;
import io.zeebe.util.StringUtil;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.IntArrayList;

public class FetchCreatedTopicsResponse
    extends SbeBufferWriterReader<
        FetchCreatedTopicsResponseEncoder, FetchCreatedTopicsResponseDecoder> {

  private final FetchCreatedTopicsResponseEncoder bodyEncoder =
      new FetchCreatedTopicsResponseEncoder();
  private final FetchCreatedTopicsResponseDecoder bodyDecoder =
      new FetchCreatedTopicsResponseDecoder();

  private List<TopicPartitions> topics = new ArrayList<>();

  public FetchCreatedTopicsResponse addTopic(String topicName, IntArrayList partitionIds) {
    topics.add(new TopicPartitions(topicName, partitionIds));
    return this;
  }

  public List<TopicPartitions> getTopics() {
    return topics;
  }

  @Override
  public void reset() {
    super.reset();
    this.topics.clear();
  }

  @Override
  protected FetchCreatedTopicsResponseEncoder getBodyEncoder() {
    return bodyEncoder;
  }

  @Override
  protected FetchCreatedTopicsResponseDecoder getBodyDecoder() {
    return bodyDecoder;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    super.wrap(buffer, offset, length);
    bodyDecoder.topics().forEach((decoder) -> topics.add(new TopicPartitions(decoder)));
  }

  @Override
  public int getLength() {
    final int length = super.getLength() + TopicsEncoder.sbeHeaderSize();
    return length + topics.stream().mapToInt(TopicPartitions::getEncodedLength).sum();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    super.write(buffer, offset);

    final int topicCount = topics.size();
    final TopicsEncoder encoder = bodyEncoder.topicsCount(topicCount);
    topics.forEach((topic) -> topic.encode(encoder));
  }

  public static class TopicPartitions {
    private String topicName;
    private IntArrayList partitionIds;

    public TopicPartitions(String topicName, IntArrayList partitionIds) {
      this.setTopicName(topicName);
      this.setPartitionIds(partitionIds);
    }

    public TopicPartitions(final TopicsDecoder decoder) {
      decode(decoder);
    }

    public int getEncodedLength() {
      return TopicsEncoder.sbeBlockLength()
          + TopicsEncoder.topicNameHeaderLength()
          + getTopicNameBytes().length
          + partitionIds.size()
              * (PartitionsEncoder.sbeBlockLength() * PartitionsEncoder.sbeHeaderSize());
    }

    void encode(final TopicsEncoder encoder) {
      final byte[] nameBytes = getTopicNameBytes();

      encoder.next().putTopicName(nameBytes, 0, nameBytes.length);

      final PartitionsEncoder partitionsEncoder = encoder.partitionsCount(partitionIds.size());
      partitionIds.forEachOrderedInt(id -> partitionsEncoder.next().partitionId(id));
    }

    void decode(final TopicsDecoder decoder) {

      setTopicName(decoder.topicName());

      final IntArrayList partitionIds = new IntArrayList();
      decoder.partitions().forEach(d -> partitionIds.add(d.partitionId()));
      setPartitionIds(partitionIds);
    }

    public byte[] getTopicNameBytes() {
      final Charset encoding = Charset.forName(TopicsEncoder.topicNameCharacterEncoding());
      return StringUtil.getBytes(topicName, encoding);
    }

    public String getTopicName() {
      return topicName;
    }

    public void setTopicName(String topicName) {
      this.topicName = topicName;
    }

    public IntArrayList getPartitionIds() {
      return partitionIds;
    }

    public void setPartitionIds(IntArrayList partitionIds) {
      this.partitionIds = partitionIds;
    }
  }
}

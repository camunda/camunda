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
package io.zeebe.raft.protocol;

import static io.zeebe.raft.PollRequestEncoder.lastEventPositionNullValue;
import static io.zeebe.raft.PollRequestEncoder.lastEventTermNullValue;
import static io.zeebe.raft.PollRequestEncoder.nodeIdNullValue;
import static io.zeebe.raft.PollRequestEncoder.partitionIdNullValue;
import static io.zeebe.raft.PollRequestEncoder.termNullValue;

import io.zeebe.logstreams.log.LogStream;
import io.zeebe.raft.PollRequestDecoder;
import io.zeebe.raft.PollRequestEncoder;
import io.zeebe.raft.Raft;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PollRequest extends AbstractRaftMessage implements HasNodeId, HasTerm, HasPartition {

  private final PollRequestDecoder bodyDecoder = new PollRequestDecoder();
  private final PollRequestEncoder bodyEncoder = new PollRequestEncoder();

  private int partitionId;
  private int term;
  private long lastEventPosition;
  private int lastEventTerm;
  private int nodeId;

  public PollRequest() {
    reset();
  }

  public PollRequest reset() {
    partitionId = partitionIdNullValue();
    term = termNullValue();
    lastEventPosition = lastEventPositionNullValue();
    lastEventTerm = lastEventTermNullValue();
    nodeId = nodeIdNullValue();

    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public int getTerm() {
    return term;
  }

  @Override
  public int getNodeId() {
    return nodeId;
  }

  public long getLastEventPosition() {
    return lastEventPosition;
  }

  public PollRequest setLastEventPosition(final long lastEventPosition) {
    this.lastEventPosition = lastEventPosition;
    return this;
  }

  public int getLastEventTerm() {
    return lastEventTerm;
  }

  public PollRequest setLastEventTerm(final int lastEventTerm) {
    this.lastEventTerm = lastEventTerm;
    return this;
  }

  @Override
  protected int getVersion() {
    return bodyDecoder.sbeSchemaVersion();
  }

  @Override
  protected int getSchemaId() {
    return bodyDecoder.sbeSchemaId();
  }

  @Override
  protected int getTemplateId() {
    return bodyDecoder.sbeTemplateId();
  }

  public PollRequest setRaft(final Raft raft) {
    final LogStream logStream = raft.getLogStream();

    partitionId = logStream.getPartitionId();
    term = raft.getTerm();
    nodeId = raft.getNodeId();

    return this;
  }

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);
    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    partitionId = bodyDecoder.partitionId();
    term = bodyDecoder.term();
    lastEventPosition = bodyDecoder.lastEventPosition();
    lastEventTerm = bodyDecoder.lastEventTerm();
    nodeId = bodyDecoder.nodeId();

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  @Override
  public void write(final MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    bodyEncoder
        .wrap(buffer, offset)
        .partitionId(partitionId)
        .term(term)
        .lastEventPosition(lastEventPosition)
        .lastEventTerm(lastEventTerm)
        .nodeId(nodeId);
  }
}

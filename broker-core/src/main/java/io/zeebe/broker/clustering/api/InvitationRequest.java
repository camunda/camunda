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

import static io.zeebe.clustering.management.InvitationRequestEncoder.*;
import static io.zeebe.clustering.management.InvitationRequestEncoder.MembersEncoder.*;

import io.zeebe.clustering.management.*;
import io.zeebe.clustering.management.InvitationRequestDecoder.MembersDecoder;
import io.zeebe.clustering.management.InvitationRequestEncoder.MembersEncoder;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class InvitationRequest implements BufferWriter, BufferReader {
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final InvitationRequestDecoder bodyDecoder = new InvitationRequestDecoder();

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final InvitationRequestEncoder bodyEncoder = new InvitationRequestEncoder();

  protected DirectBuffer topicName = new UnsafeBuffer(0, 0);
  protected int partitionId = partitionIdNullValue();
  protected int replicationFactor = replicationFactorNullValue();

  protected int term = termNullValue();
  protected List<SocketAddress> members = new CopyOnWriteArrayList<>();

  public int partitionId() {
    return partitionId;
  }

  public InvitationRequest partitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  public int replicationFactor() {
    return replicationFactor;
  }

  public InvitationRequest replicationFactor(int replicationFactor) {
    this.replicationFactor = replicationFactor;
    return this;
  }

  public DirectBuffer topicName() {
    return topicName;
  }

  public InvitationRequest topicName(final DirectBuffer topicName) {
    this.topicName.wrap(topicName);
    return this;
  }

  public int term() {
    return term;
  }

  public InvitationRequest term(final int term) {
    this.term = term;
    return this;
  }

  public List<SocketAddress> members() {
    return members;
  }

  public InvitationRequest members(final List<SocketAddress> members) {
    this.members.clear();
    this.members.addAll(members);
    return this;
  }

  @Override
  public int getLength() {
    final int size = members.size();

    int length = headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();

    length += sbeHeaderSize() + (sbeBlockLength() + hostHeaderLength()) * size;

    for (int i = 0; i < size; i++) {
      length += members.get(i).hostLength();
    }

    length += topicNameHeaderLength();

    if (topicName != null) {
      length += topicName.capacity();
    }

    return length;
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

    final int size = members.size();

    final MembersEncoder encoder =
        bodyEncoder
            .wrap(buffer, offset)
            .partitionId(partitionId)
            .replicationFactor(replicationFactor)
            .term(term)
            .membersCount(size);

    for (int i = 0; i < size; i++) {
      final SocketAddress member = members.get(i);

      encoder.next().port(member.port()).putHost(member.getHostBuffer(), 0, member.hostLength());
    }

    bodyEncoder.putTopicName(topicName, 0, topicName.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, int offset, final int length) {
    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);
    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    partitionId = bodyDecoder.partitionId();
    replicationFactor = bodyDecoder.replicationFactor();
    term = bodyDecoder.term();

    members.clear();

    final Iterator<MembersDecoder> iterator = bodyDecoder.members().iterator();

    while (iterator.hasNext()) {
      final MembersDecoder decoder = iterator.next();

      final SocketAddress member = new SocketAddress();
      member.port(decoder.port());

      final MutableDirectBuffer hostBuffer = member.getHostBuffer();
      final int hostLength = decoder.hostLength();
      member.hostLength(hostLength);
      decoder.getHost(hostBuffer, 0, hostLength);

      members.add(member);
    }

    final int topicNameLength = bodyDecoder.topicNameLength();
    final int topicNameOffset = bodyDecoder.limit() + topicNameHeaderLength();
    topicName.wrap(buffer, topicNameOffset, topicNameLength);

    // skip topic name in decoder
    bodyDecoder.limit(topicNameOffset + topicNameLength);

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  public void reset() {
    topicName.wrap(0, 0);
    partitionId = partitionIdNullValue();
    term = termNullValue();
    members.clear();
  }
}

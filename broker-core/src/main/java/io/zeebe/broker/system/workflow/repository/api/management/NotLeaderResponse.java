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
package io.zeebe.broker.system.workflow.repository.api.management;

import io.zeebe.clustering.management.*;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class NotLeaderResponse implements BufferReader, BufferWriter {
  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final NotLeaderResponseEncoder bodyEncoder = new NotLeaderResponseEncoder();

  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  private final NotLeaderResponseDecoder bodyDecoder = new NotLeaderResponseDecoder();

  @Override
  public int getLength() {
    return headerEncoder.encodedLength() + bodyEncoder.sbeBlockLength();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength());
  }

  public boolean tryWrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    return headerDecoder.schemaId() == bodyDecoder.sbeSchemaId()
        && headerDecoder.templateId() == bodyDecoder.sbeTemplateId();
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
  }
}

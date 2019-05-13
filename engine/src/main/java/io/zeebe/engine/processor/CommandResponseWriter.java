/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor;

import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;

public interface CommandResponseWriter {

  CommandResponseWriter partitionId(int partitionId);

  CommandResponseWriter key(long key);

  CommandResponseWriter intent(Intent intent);

  CommandResponseWriter recordType(RecordType type);

  CommandResponseWriter valueType(ValueType valueType);

  CommandResponseWriter rejectionType(RejectionType rejectionType);

  CommandResponseWriter rejectionReason(DirectBuffer rejectionReason);

  CommandResponseWriter valueWriter(BufferWriter value);

  boolean tryWriteResponse(int requestStreamId, long requestId);
}

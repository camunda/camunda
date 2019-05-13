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

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.intent.Intent;
import java.util.function.Consumer;

/** Things that only a stream processor should write to the log stream (+ commands) */
public interface TypedStreamWriter extends TypedCommandWriter {
  void appendRejection(
      TypedRecord<? extends UnpackedObject> command, RejectionType type, String reason);

  void appendRejection(
      TypedRecord<? extends UnpackedObject> command,
      RejectionType type,
      String reason,
      Consumer<RecordMetadata> metadata);

  void appendNewEvent(long key, Intent intent, UnpackedObject value);

  void appendFollowUpEvent(long key, Intent intent, UnpackedObject value);

  void appendFollowUpEvent(
      long key, Intent intent, UnpackedObject value, Consumer<RecordMetadata> metadata);
}

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
package io.zeebe.engine.util;

import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.Intent;
import io.zeebe.test.util.stream.StreamWrapper;
import java.util.stream.Stream;

public class TypedRecordStream<T extends UnifiedRecordValue>
    extends StreamWrapper<Record<T>, TypedRecordStream<T>> {

  public TypedRecordStream(Stream<Record<T>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TypedRecordStream<T> supply(Stream<Record<T>> wrappedStream) {
    return new TypedRecordStream<>(wrappedStream);
  }

  public TypedRecordStream<T> onlyCommands() {
    return new TypedRecordStream<>(filter(r -> r.getRecordType() == RecordType.COMMAND));
  }

  public TypedRecordStream<T> onlyEvents() {
    return new TypedRecordStream<>(filter(r -> r.getRecordType() == RecordType.EVENT));
  }

  public TypedRecordStream<T> onlyRejections() {
    return new TypedRecordStream<>(filter(r -> r.getRecordType() == RecordType.COMMAND_REJECTION));
  }

  public TypedRecordStream<T> withIntent(Intent intent) {
    return new TypedRecordStream<>(filter(r -> r.getIntent() == intent));
  }
}

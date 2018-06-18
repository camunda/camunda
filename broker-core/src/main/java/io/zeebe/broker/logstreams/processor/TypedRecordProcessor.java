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
package io.zeebe.broker.logstreams.processor;

import io.zeebe.logstreams.processor.EventLifecycleContext;
import io.zeebe.logstreams.processor.EventProcessor;
import io.zeebe.msgpack.UnpackedObject;

public interface TypedRecordProcessor<T extends UnpackedObject>
    extends StreamProcessorLifecycleAware {
  /** @see EventProcessor#processEvent() */
  default void processRecord(TypedRecord<T> record) {}

  /** @see EventProcessor#processEvent() */
  default void processRecord(TypedRecord<T> record, EventLifecycleContext ctx) {
    processRecord(record);
  }

  /** @see EventProcessor#executeSideEffects() */
  default boolean executeSideEffects(TypedRecord<T> record, TypedResponseWriter responseWriter) {
    return true;
  }

  /** @see EventProcessor#writeEvent(io.zeebe.logstreams.log.LogStreamWriter) */
  default long writeRecord(TypedRecord<T> record, TypedStreamWriter writer) {
    return 0;
  }

  /** @see EventProcessor#updateState() */
  default void updateState(TypedRecord<T> record) {}
}

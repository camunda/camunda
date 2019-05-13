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
import java.util.function.Consumer;

public interface TypedRecordProcessor<T extends UnpackedObject>
    extends StreamProcessorLifecycleAware {

  /** @see #processRecord(TypedRecord, TypedResponseWriter, TypedStreamWriter, Consumer) */
  default void processRecord(
      TypedRecord<T> record, TypedResponseWriter responseWriter, TypedStreamWriter streamWriter) {}

  /** @see #processRecord(TypedRecord, TypedResponseWriter, TypedStreamWriter, Consumer) */
  default void processRecord(
      TypedRecord<T> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    processRecord(record, responseWriter, streamWriter);
  }

  /**
   * @param position the position of the current record to process
   * @param record the record to process
   * @param responseWriter the default side effect that can be used for sending responses. {@link
   *     TypedResponseWriter#flush()} must not be called in this method.
   * @param streamWriter
   * @param sideEffect consumer to replace the default side effect (response writer). Can be used to
   *     implement other types of side effects or composite side effects. If a composite side effect
   *     involving the response writer is used, {@link TypedResponseWriter#flush()} must be called
   *     in the {@link SideEffectProducer} implementation.
   */
  default void processRecord(
      long position,
      TypedRecord<T> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect) {
    processRecord(record, responseWriter, streamWriter, sideEffect);
  }
}

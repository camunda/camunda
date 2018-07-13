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
import io.zeebe.msgpack.UnpackedObject;
import java.util.function.Consumer;

public interface TypedRecordProcessor<T extends UnpackedObject>
    extends StreamProcessorLifecycleAware {

  /**
   * @see #processRecord(TypedRecord, TypedResponseWriter, TypedStreamWriter, Consumer,
   *     EventLifecycleContext)
   */
  default void processRecord(
      TypedRecord<T> record, TypedResponseWriter responseWriter, TypedStreamWriter streamWriter) {}

  /**
   * @param record
   * @param responseWriter the default side effect that can be used for sending responses. {@link
   *     TypedResponseWriter#flush()} must not be called in this method.
   * @param streamWriter
   * @param sideEffect consumer to replace the default side effect (response writer). Can be used to
   *     implement other types of side effects or composite side effects. If a composite side effect
   *     involving the response writer is used, {@link TypedResponseWriter#flush()} must be called
   *     in the {@link SideEffectProducer} implementation.
   * @param ctx use {@link EventLifecycleContext#async(io.zeebe.util.sched.future.ActorFuture)} to
   *     submit an asynchronous invocation that processing the record depends on.
   */
  default void processRecord(
      TypedRecord<T> record,
      TypedResponseWriter responseWriter,
      TypedStreamWriter streamWriter,
      Consumer<SideEffectProducer> sideEffect,
      EventLifecycleContext ctx) {
    processRecord(record, responseWriter, streamWriter);
  }
}

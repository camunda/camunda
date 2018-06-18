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
package io.zeebe.broker.transport.controlmessage;

import io.zeebe.broker.transport.clientapi.ErrorResponseWriter;
import io.zeebe.protocol.clientapi.ErrorCode;
import io.zeebe.transport.ServerOutput;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorControl;
import java.util.function.BooleanSupplier;

public abstract class AbstractControlMessageHandler implements ControlMessageHandler {
  protected final ControlMessageResponseWriter responseWriter;
  protected final ErrorResponseWriter errorResponseWriter;

  public AbstractControlMessageHandler(final ServerOutput output) {
    this.errorResponseWriter = new ErrorResponseWriter(output);
    this.responseWriter = new ControlMessageResponseWriter(output);
  }

  protected void sendResponse(
      final ActorControl actor,
      final int streamId,
      final long requestId,
      final BufferWriter dataWriter) {
    sendResponse(
        actor, () -> responseWriter.dataWriter(dataWriter).tryWriteResponse(streamId, requestId));
  }

  protected void sendErrorResponse(
      final ActorControl actor,
      final int streamId,
      final long requestId,
      final String errorMessage,
      final Object... args) {
    sendErrorResponse(
        actor, streamId, requestId, ErrorCode.REQUEST_PROCESSING_FAILURE, errorMessage, args);
  }

  protected void sendErrorResponse(
      final ActorControl actor,
      final int streamId,
      final long requestId,
      final ErrorCode errorCode,
      final String errorMessage,
      final Object... args) {
    sendResponse(
        actor,
        () ->
            errorResponseWriter
                .errorCode(errorCode)
                .errorMessage(errorMessage, args)
                .tryWriteResponse(streamId, requestId));
  }

  protected void sendResponse(final ActorControl actor, final BooleanSupplier supplier) {
    actor.runUntilDone(
        () -> {
          final boolean success = supplier.getAsBoolean();

          if (success) {
            actor.done();
          } else {
            actor.yield();
          }
        });
  }
}

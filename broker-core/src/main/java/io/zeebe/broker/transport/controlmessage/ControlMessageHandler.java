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

import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.util.sched.ActorControl;
import org.agrona.DirectBuffer;

/** Handle a specific type of control messages. */
public interface ControlMessageHandler {
  /** Returns the type of control message which can be handled. */
  ControlMessageType getMessageType();

  /**
   * Handle the given control message asynchronously. An implementation may copy the buffer if the
   * data is used beyond the invocation.
   *
   * <p>Backpressure should be implemented in the {@link #handle(ActorControl, int, DirectBuffer,
   * RecordMetadata)} methdod like follows: Sending response (success or error) should be done via
   * actor.runUntilDone. This will block the calling actor, until the response is send successfully.
   *
   * @param actor the actor that can be used for waiting of async calls
   * @param partitionId < 0 if no specific partition is addressed
   * @param buffer the buffer which contains the control message as MsgPack-JSON
   * @param metadata the metadata (channel partitionId, connection partitionId, request partitionId)
   *     of the request
   */
  void handle(ActorControl actor, int partitionId, DirectBuffer buffer, RecordMetadata metadata);
}

/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.transport.controlmessage;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;

import io.zeebe.broker.logstreams.BrokerEventMetadata;
import io.zeebe.protocol.clientapi.ControlMessageType;

/**
 * Handle a specific type of control messages.
 */
public interface ControlMessageHandler
{
    /**
     * Returns the type of control message which can be handled.
     */
    ControlMessageType getMessageType();

    /**
     * Handle the given control message asynchronously. An implementation may
     * copy the buffer if the data is used beyond the invocation.
     *
     * @param buffer
     *            the buffer which contains the control message as MsgPack-JSON
     * @param metadata
     *            the metadata (channel partitionId, connection partitionId, request partitionId) of the
     *            request
     * @return a future which indicates when the control message is handled
     *         completely
     */
    CompletableFuture<Void> handle(DirectBuffer buffer, BrokerEventMetadata metadata);
}

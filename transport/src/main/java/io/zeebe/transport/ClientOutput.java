/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.agrona.DirectBuffer;

import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;

public interface ClientOutput
{
    /**
     * <p>Sends a message according to the single message protocol.
     *
     * <p>Returns false if the message cannot be currently written due to exhausted capacity.
     * Throws an exception if the request is not sendable at all (e.g. buffer writer throws exception).
     */
    boolean sendMessage(TransportMessage transportMessage);

    /**
     * Same as {@link #sendRequest(RemoteAddress, BufferWriter, long)} where the timeout is set to the configured default timeout.
     * @return the response future
     */
    ActorFuture<ClientResponse> sendRequest(RemoteAddress addr, BufferWriter writer);

    /**
     * <p>Like {@link #sendRequest(RemoteAddress, BufferWriter)} but retries the request if there is no current connection.
     * Makes this method more robust in the presence of short intermittent disconnects.
     *
     * <p>Guarantees:
     * <ul>
     * <li>Not garbage-free
     * <li>n intermediary copies of the request (one local copy for making retries, one copy on the send buffer per try)
     *
     * @param timeout Timeout in milliseconds until the returned future fails if no response is received.
     * @return the response future
     */
    ActorFuture<ClientResponse> sendRequest(RemoteAddress addr, BufferWriter writer, Duration timeout);

    /**
     * <p>Like {@link #sendRequest(RemoteAddress, BufferWriter)} but retries the request if there is no current connection.
     * Makes this method more robust in the presence of short intermittent disconnects.
     *
     * <p>Guarantees:
     * <ul>
     * <li>Not garbage-free
     * <li>n intermediary copies of the request (one local copy for making retries, one copy on the send buffer per try)
     *
     * @param remoteAddressSupplier
     *            supplier for the remote address the retries are executed against (retries may
     *            be executed against different remotes). The future may resolve to <code>null</code> to signal that
     *            a remote can not be determined. In that case, the request is retried after resubmit timeout.
     * @param responseInspector
     *            function getting the response and returning a boolean. If the function returns true,
     *            the request will be retried: usecase: in a system like zeebe, we may send a request to the
     *            wrong node. The node will send a response indicating that it is not able to handle this request.
     *            In this case we want to do a retry and send the request to a different node, based on the content
     *            of the response
     * @param timeout The timeout until the returned future fails if no response is received.
     * @return the last request which eventually succeeded.
     */
    ActorFuture<ClientResponse> sendRequestWithRetry(Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier, Predicate<DirectBuffer> responseInspector, BufferWriter writer, Duration timeout);
}
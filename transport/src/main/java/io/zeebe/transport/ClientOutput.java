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

import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

public interface ClientOutput {

  /**
   * Sends a message according to the single message protocol.
   *
   * <p>Returns false if the message cannot be currently written due to an unknown endpoint or
   * exhausted capacity. Throws an exception if the request is not sendable at all (e.g. buffer
   * writer throws exception).
   */
  boolean sendMessage(Integer nodeId, BufferWriter writer);

  /**
   * Like {@link #sendRequest(Integer, BufferWriter, Duration)} where the timeout is set to the
   * configured default timeout.
   *
   * @return the response future or null in case no memory is currently available to allocate the
   *     request
   */
  ActorFuture<ClientResponse> sendRequest(Integer nodeId, BufferWriter writer);

  /**
   * Like {@link #sendRequestWithRetry(Supplier, Predicate, BufferWriter, Duration)} with a static
   * remote and no response inspection (i.e. first response is accepted).
   *
   * @return the response future or null in case no memory is currently available to allocate the
   *     request
   */
  ActorFuture<ClientResponse> sendRequest(Integer nodeId, BufferWriter writer, Duration timeout);

  /**
   * Send a request to a node with retries if there is no current connection or the node is not
   * resolvable. Makes this method more robust in the presence of short intermittent disconnects.
   *
   * <p>Guarantees:
   *
   * <ul>
   *   <li>Not garbage-free
   *   <li>n intermediary copies of the request (one local copy for making retries, one copy on the
   *       send buffer per try)
   *
   * @param nodeIdSupplier supplier for the node id the retries are executed against (retries may be
   *     executed against different nodes). The supplier may resolve to <code>null
   *     </code> to signal that a node id can not be determined. In that case, the request is
   *     retried after resubmit timeout.
   * @param responseInspector function getting the response and returning a boolean. If the function
   *     returns true, the request will be retried: usecase: in a system like zeebe, we may send a
   *     request to the wrong node. The node will send a response indicating that it is not able to
   *     handle this request. In this case we want to do a retry and send the request to a different
   *     node, based on the content of the response
   * @param timeout The timeout until the returned future fails if no response is received.
   * @return a future carrying the response that was accepted or null in case no memory is currently
   *     available to allocate the request. Can complete exceptionally in failure cases such as
   *     timeout.
   */
  ActorFuture<ClientResponse> sendRequestWithRetry(
      Supplier<Integer> nodeIdSupplier,
      Predicate<DirectBuffer> responseInspector,
      BufferWriter writer,
      Duration timeout);
}

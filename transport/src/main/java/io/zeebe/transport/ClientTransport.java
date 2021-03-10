/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.transport;

import io.zeebe.util.sched.future.ActorFuture;
import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

public interface ClientTransport extends AutoCloseable {
  /**
   * Similar to {@link #sendRequestWithRetry(Supplier, Predicate, ClientRequest, Duration)}, but no
   * requests are validated before completing the future.
   *
   * <p>Send a request to a node with retries if there is no current connection or the node is not
   * resolvable. Makes this method more robust in the presence of short intermittent disconnects.
   *
   * <p>Guarantees:
   *
   * <ul>
   *   <li>Not garbage-free
   *   <li>n intermediary copies of the request (one local copy for making retries, one copy on the
   *       send buffer per try)
   *
   * @param nodeAddressSupplier supplier for the node address the retries are executed against
   *     (retries may be executed against different nodes). The supplier may resolve to <code>null
   *     </code> to signal that a node address can not be determined. In that case, the request is
   *     retried after resubmit timeout.
   * @param clientRequest the request which should be send
   * @param timeout The timeout until the returned future fails if no response is received.
   * @return a future carrying the response that was accepted or null in case no memory is currently
   *     available to allocate the request. Can complete exceptionally in failure cases such as
   *     timeout.
   */
  default ActorFuture<DirectBuffer> sendRequestWithRetry(
      final Supplier<String> nodeAddressSupplier,
      final ClientRequest clientRequest,
      final Duration timeout) {
    return sendRequestWithRetry(nodeAddressSupplier, response -> true, clientRequest, timeout);
  }

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
   * @param nodeAddressSupplier supplier for the node address the retries are executed against
   *     (retries may be executed against different nodes). The supplier may resolve to <code>null
   *     </code> to signal that a node address can not be determined. In that case, the request is
   *     retried after resubmit timeout.
   * @param responseValidator predicate which tests the received response, before completing the
   *     future to verify, whether this request needs to be retried or not, in respect of the
   *     current timeout. This avoids retrying, without new copy of the corresponding request and no
   *     separate logic in the client. When the validator returns *true* then the request is valid
   *     and should not be retried.
   * @param clientRequest the request which should be send
   * @param timeout The timeout until the returned future fails if no response is received.
   * @return a future carrying the response that was accepted or null in case no memory is currently
   *     available to allocate the request. Can complete exceptionally in failure cases such as
   *     timeout.
   */
  ActorFuture<DirectBuffer> sendRequestWithRetry(
      Supplier<String> nodeAddressSupplier,
      Predicate<DirectBuffer> responseValidator,
      ClientRequest clientRequest,
      Duration timeout);

  /**
   * Send a request to a node with out any retries.
   *
   * <p>Guarantees:
   *
   * <ul>
   *   <li>Not garbage-free
   *   <li>1 intermediary copies of the request
   *
   * @param nodeAddressSupplier supplier for the node address the retries are executed against
   *     (retries may be executed against different nodes). The supplier may resolve to <code>null
   *     </code> to signal that a node address can not be determined. In that case, the request will
   *     be completed with a NoRemoteAddressFoundException.
   * @param clientRequest the request which should be send
   * @param timeout The timeout until the returned future fails if no response is received.
   * @return a future carrying the response that was accepted or null in case no memory is currently
   *     available to allocate the request. Can complete exceptionally in failure cases such as
   *     timeout.
   */
  ActorFuture<DirectBuffer> sendRequest(
      Supplier<String> nodeAddressSupplier, ClientRequest clientRequest, Duration timeout);
}

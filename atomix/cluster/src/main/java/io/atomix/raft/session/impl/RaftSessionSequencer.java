/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.session.impl;

import com.google.common.annotations.VisibleForTesting;
import io.atomix.primitive.session.SessionClient;
import io.atomix.raft.protocol.OperationResponse;
import io.atomix.raft.protocol.PublishRequest;
import io.atomix.utils.logging.ContextualLoggerFactory;
import io.atomix.utils.logging.LoggerContext;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import org.slf4j.Logger;

/**
 * Client response sequencer.
 *
 * <p>The way operations are applied to replicated state machines, allows responses to be handled in
 * a consistent manner. Command responses will always have an {@code eventIndex} less than the
 * response {@code index}. This is because commands always occur <em>before</em> the events they
 * trigger, and because events are always associated with a command index and never a query index,
 * the previous {@code eventIndex} for a command response will always be less than the response
 * {@code index}.
 *
 * <p>Alternatively, the previous {@code eventIndex} for a query response may be less than or equal
 * to the response {@code index}. However, in contrast to commands, queries always occur
 * <em>after</em> prior events. This means for a given index, the precedence is command -> event ->
 * query.
 *
 * <p>Since operations for an index will always occur in a consistent order, sequencing operations
 * is a trivial task. When a response is received, once the response is placed in sequential order,
 * pending events up to the response's {@code eventIndex} may be completed. Because command
 * responses will never have an {@code eventIndex} equal to their own response {@code index}, events
 * will always stop prior to the command. But query responses may have an {@code eventIndex} equal
 * to their own response {@code index}, and in that case the event will be completed prior to the
 * completion of the query response.
 *
 * <p>Events can also be received later than sequenced operations. When an event is received, it's
 * first placed in sequential order as is the case with operation responses. Once placed in
 * sequential order, if no requests are outstanding, the event is immediately completed. This
 * ensures that events that are published during a period of inactivity in the session can still be
 * completed upon reception since the event is guaranteed not to have occurred concurrently with any
 * other operation. If requests for the session are outstanding, the event is placed in a queue and
 * the algorithm for checking sequenced responses is run again.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
final class RaftSessionSequencer {

  @VisibleForTesting long requestSequence;
  @VisibleForTesting long responseSequence;
  @VisibleForTesting long eventIndex;

  private final Logger log;
  private final RaftSessionState state;
  private final Queue<EventCallback> eventCallbacks = new ArrayDeque<>();
  private final Map<Long, ResponseCallback> responseCallbacks = new HashMap<>();

  RaftSessionSequencer(final RaftSessionState state) {
    this.state = state;
    this.log =
        ContextualLoggerFactory.getLogger(
            getClass(),
            LoggerContext.builder(SessionClient.class)
                .addValue(state.getSessionId())
                .add("type", state.getPrimitiveType())
                .add("name", state.getPrimitiveName())
                .build());
  }

  /**
   * Returns the next request sequence number.
   *
   * @return The next request sequence number.
   */
  public long nextRequest() {
    return ++requestSequence;
  }

  /**
   * Sequences an event.
   *
   * <p>This method relies on the session event protocol to ensure that events are applied in
   * sequential order. When an event is received, if no operations are outstanding, the event is
   * immediately completed since the event could not have occurred concurrently with any other
   * operation. Otherwise, the event is queued and the next response in the sequence of responses is
   * checked to determine whether the event can be completed.
   *
   * @param request The publish request.
   * @param callback The callback to sequence.
   */
  public void sequenceEvent(final PublishRequest request, final Runnable callback) {
    if (requestSequence == responseSequence) {
      log.trace("Completing {}", request);
      callback.run();
      eventIndex = request.eventIndex();
    } else {
      eventCallbacks.add(new EventCallback(request, callback));
      completeResponses();
    }
  }

  /** Completes all sequenced responses. */
  private void completeResponses() {
    // Iterate through queued responses and complete as many as possible.
    ResponseCallback response = responseCallbacks.get(responseSequence + 1);
    while (response != null) {
      // If the response was completed, remove the response callback from the response queue,
      // increment the response sequence number, and check the next response.
      if (completeResponse(response.response, response.callback)) {
        responseCallbacks.remove(++responseSequence);
        response = responseCallbacks.get(responseSequence + 1);
      } else {
        break;
      }
    }

    // Once we've completed as many responses as possible, if no more operations are outstanding
    // and events remain in the event queue, complete the events.
    if (requestSequence == responseSequence) {
      EventCallback eventCallback = eventCallbacks.poll();
      while (eventCallback != null) {
        log.trace("Completing {}", eventCallback.request);
        eventCallback.run();
        eventIndex = eventCallback.request.eventIndex();
        eventCallback = eventCallbacks.poll();
      }
    }
  }

  /** Completes a sequenced response if possible. */
  private boolean completeResponse(final OperationResponse response, final Runnable callback) {
    // If the response is null, that indicates an exception occurred. The best we can do is complete
    // the response in sequential order.
    if (response == null) {
      log.trace("Completing failed request");
      callback.run();
      return true;
    }

    // If the response's event index is greater than the current event index, that indicates that
    // events that were
    // published prior to the response have not yet been completed. Attempt to complete pending
    // events.
    if (response.eventIndex() > eventIndex) {
      // For each pending event with an eventIndex less than or equal to the response eventIndex,
      // complete the event.
      // This is safe since we know that sequenced responses should see sequential order of events.
      EventCallback eventCallback = eventCallbacks.peek();
      while (eventCallback != null && eventCallback.request.eventIndex() <= response.eventIndex()) {
        eventCallbacks.remove();
        log.trace("Completing event {}", eventCallback.request);
        eventCallback.run();
        eventIndex = eventCallback.request.eventIndex();
        eventCallback = eventCallbacks.peek();
      }
    }

    // If after completing pending events the eventIndex is greater than or equal to the response's
    // eventIndex, complete the response.
    // Note that the event protocol initializes the eventIndex to the session ID.
    if (response.eventIndex() <= eventIndex
        || (eventIndex == 0 && response.eventIndex() == state.getSessionId().id())) {
      log.trace("Completing response {}", response);
      callback.run();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Sequences a response.
   *
   * <p>When an operation is sequenced, it's first sequenced in the order in which it was submitted
   * to the cluster. Once placed in sequential request order, if a response's {@code eventIndex} is
   * greater than the last completed {@code eventIndex}, we attempt to sequence pending events. If
   * after sequencing pending events the response's {@code eventIndex} is equal to the last
   * completed {@code eventIndex} then the response can be immediately completed. If not enough
   * events are pending to meet the sequence requirement, the sequencing of responses is stopped
   * until events are received.
   *
   * @param sequence The request sequence number.
   * @param response The response to sequence.
   * @param callback The callback to sequence.
   */
  public void sequenceResponse(
      final long sequence, final OperationResponse response, final Runnable callback) {
    // If the request sequence number is equal to the next response sequence number, attempt to
    // complete the response.
    if (sequence == responseSequence + 1) {
      if (completeResponse(response, callback)) {
        ++responseSequence;
        completeResponses();
      } else {
        responseCallbacks.put(sequence, new ResponseCallback(response, callback));
      }
    }
    // If the response has not yet been sequenced, store it in the response callbacks map.
    // Otherwise, the response for the operation with this sequence number has already been handled.
    else if (sequence > responseSequence) {
      responseCallbacks.put(sequence, new ResponseCallback(response, callback));
    }
  }

  /** Response callback holder. */
  private static final class ResponseCallback implements Runnable {

    private final OperationResponse response;
    private final Runnable callback;

    private ResponseCallback(final OperationResponse response, final Runnable callback) {
      this.response = response;
      this.callback = callback;
    }

    @Override
    public void run() {
      callback.run();
    }
  }

  /** Event callback holder. */
  private static final class EventCallback implements Runnable {

    private final PublishRequest request;
    private final Runnable callback;

    private EventCallback(final PublishRequest request, final Runnable callback) {
      this.request = request;
      this.callback = callback;
    }

    @Override
    public void run() {
      callback.run();
    }
  }
}

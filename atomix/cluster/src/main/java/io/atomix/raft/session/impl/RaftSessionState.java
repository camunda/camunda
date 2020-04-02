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

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.primitive.PrimitiveState;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.session.SessionId;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/** Client state. */
public final class RaftSessionState {

  private final String clientId;
  private final SessionId sessionId;
  private final String serviceName;
  private final PrimitiveType primitiveType;
  private final long timeout;
  private final Set<Consumer<PrimitiveState>> changeListeners = new CopyOnWriteArraySet<>();
  private volatile PrimitiveState state = PrimitiveState.CONNECTED;
  private volatile Long suspendedTime;
  private volatile long commandRequest;
  private volatile long commandResponse;
  private volatile long responseIndex;
  private volatile long eventIndex;

  RaftSessionState(
      final String clientId,
      final SessionId sessionId,
      final String serviceName,
      final PrimitiveType primitiveType,
      final long timeout) {
    this.clientId = clientId;
    this.sessionId = sessionId;
    this.serviceName = serviceName;
    this.primitiveType = primitiveType;
    this.timeout = timeout;
    this.responseIndex = sessionId.id();
    this.eventIndex = sessionId.id();
  }

  /**
   * Registers a state change listener on the session manager.
   *
   * @param listener The state change listener callback.
   */
  public void addStateChangeListener(final Consumer<PrimitiveState> listener) {
    changeListeners.add(checkNotNull(listener));
  }

  /**
   * Removes a state change listener.
   *
   * @param listener the listener to remove
   */
  public void removeStateChangeListener(final Consumer<PrimitiveState> listener) {
    changeListeners.remove(checkNotNull(listener));
  }

  /**
   * Returns the next command request sequence number for the session.
   *
   * @return The next command request sequence number for the session.
   */
  public long nextCommandRequest() {
    return ++commandRequest;
  }

  /**
   * Returns the client identifier.
   *
   * @return The client identifier.
   */
  public String getClientId() {
    return clientId;
  }

  /**
   * Returns the last command request sequence number for the session.
   *
   * @return The last command request sequence number for the session.
   */
  public long getCommandRequest() {
    return commandRequest;
  }

  /**
   * Sets the last command request sequence number.
   *
   * @param commandRequest The last command request sequence number.
   */
  public void setCommandRequest(final long commandRequest) {
    this.commandRequest = commandRequest;
  }

  /**
   * Returns the last command sequence number for which a response has been received.
   *
   * @return The last command sequence number for which a response has been received.
   */
  public long getCommandResponse() {
    return commandResponse;
  }

  /**
   * Sets the last command sequence number for which a response has been received.
   *
   * @param commandResponse The last command sequence number for which a response has been received.
   */
  public void setCommandResponse(final long commandResponse) {
    this.commandResponse = commandResponse;
  }

  /**
   * Returns the highest index for which an event has been received in sequence.
   *
   * @return The highest index for which an event has been received in sequence.
   */
  public long getEventIndex() {
    return eventIndex;
  }

  /**
   * Sets the highest index for which an event has been received in sequence.
   *
   * @param eventIndex The highest index for which an event has been received in sequence.
   */
  public void setEventIndex(final long eventIndex) {
    this.eventIndex = eventIndex;
  }

  /**
   * Returns the session name.
   *
   * @return The session name.
   */
  public String getPrimitiveName() {
    return serviceName;
  }

  /**
   * Returns the session type.
   *
   * @return The session type.
   */
  public PrimitiveType getPrimitiveType() {
    return primitiveType;
  }

  /**
   * Returns the highest index for which a response has been received.
   *
   * @return The highest index for which a command or query response has been received.
   */
  public long getResponseIndex() {
    return responseIndex;
  }

  /**
   * Sets the highest index for which a response has been received.
   *
   * @param responseIndex The highest index for which a command or query response has been received.
   */
  public void setResponseIndex(final long responseIndex) {
    this.responseIndex = Math.max(this.responseIndex, responseIndex);
  }

  /**
   * Returns the client session ID.
   *
   * @return The client session ID.
   */
  public SessionId getSessionId() {
    return sessionId;
  }

  /**
   * Returns the session timeout.
   *
   * @return The session timeout.
   */
  public long getSessionTimeout() {
    return timeout;
  }

  /**
   * Returns the session state.
   *
   * @return The session state.
   */
  public PrimitiveState getState() {
    return state;
  }

  /**
   * Updates the session state.
   *
   * @param state The updates session state.
   */
  public void setState(final PrimitiveState state) {
    if (this.state != state) {
      if (this.state != PrimitiveState.EXPIRED && this.state != PrimitiveState.CLOSED) {
        this.state = state;
        updateSuspendTime(state);
        changeListeners.forEach(l -> l.accept(state));
      }
    } else if (this.state == PrimitiveState.SUSPENDED) {
      if (System.currentTimeMillis() - suspendedTime > timeout) {
        setState(PrimitiveState.EXPIRED);
      }
    }
  }

  private void updateSuspendTime(final PrimitiveState state) {
    if (state == PrimitiveState.SUSPENDED) {
      if (suspendedTime == null) {
        suspendedTime = System.currentTimeMillis();
      }
    } else {
      suspendedTime = null;
    }
  }
}

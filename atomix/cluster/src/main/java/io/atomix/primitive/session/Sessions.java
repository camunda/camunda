/*
 * Copyright 2015-present Open Networking Foundation
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

package io.atomix.primitive.session;

import io.atomix.primitive.event.PrimitiveEvent;

/**
 * Provides a set of active server sessions.
 *
 * <p>Server state machines can use the {@code Sessions} object to access the list of sessions
 * currently open to the state machine. Session sets are guaranteed to be deterministic. All state
 * machines will see the same set of open sessions at the same point in the log except in cases
 * where a session has already been closed and removed. If a session has already been closed on
 * another server, the session is guaranteed to have been expired on all servers and thus operations
 * like {@link Session#publish(PrimitiveEvent)} are effectively no-ops.
 */
public interface Sessions extends Iterable<Session> {

  /**
   * Returns a session by session ID.
   *
   * @param sessionId The session ID.
   * @return The session or {@code null} if no session with the given {@code sessionId} exists.
   */
  Session getSession(long sessionId);

  /**
   * Adds a listener to the sessions.
   *
   * @param listener The listener to add.
   * @return The sessions.
   * @throws NullPointerException if the session {@code listener} is {@code null}
   */
  Sessions addListener(SessionListener listener);

  /**
   * Removes a listener from the sessions.
   *
   * @param listener The listener to remove.
   * @return The sessions.
   */
  Sessions removeListener(SessionListener listener);
}

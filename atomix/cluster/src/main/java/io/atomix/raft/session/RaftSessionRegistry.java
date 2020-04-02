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
package io.atomix.raft.session;

import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.session.SessionId;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Session manager. */
public class RaftSessionRegistry {

  private final Map<Long, RaftSession> sessions = new ConcurrentHashMap<>();

  /** Adds a session. */
  public RaftSession addSession(final RaftSession session) {
    final RaftSession existingSession = sessions.putIfAbsent(session.sessionId().id(), session);
    return existingSession != null ? existingSession : session;
  }

  /** Closes a session. */
  public RaftSession removeSession(final SessionId sessionId) {
    return sessions.remove(sessionId.id());
  }

  /**
   * Gets a session by session ID.
   *
   * @param sessionId The session ID.
   * @return The session or {@code null} if the session doesn't exist.
   */
  public RaftSession getSession(final SessionId sessionId) {
    return getSession(sessionId.id());
  }

  /**
   * Gets a session by session ID.
   *
   * @param sessionId The session ID.
   * @return The session or {@code null} if the session doesn't exist.
   */
  public RaftSession getSession(final long sessionId) {
    return sessions.get(sessionId);
  }

  /**
   * Returns a set of sessions associated with the given service.
   *
   * @param primitiveId the service identifier
   * @return a collection of sessions associated with the given service
   */
  public Collection<RaftSession> getSessions(final PrimitiveId primitiveId) {
    return sessions.values().stream()
        .filter(session -> session.getService().serviceId().equals(primitiveId))
        .filter(session -> session.getState().active())
        .collect(Collectors.toSet());
  }

  /**
   * Removes all sessions registered for the given service.
   *
   * @param primitiveId the service identifier
   */
  public void removeSessions(final PrimitiveId primitiveId) {
    sessions.entrySet().removeIf(e -> e.getValue().getService().serviceId().equals(primitiveId));
  }

  /**
   * Returns the collection of registered sessions.
   *
   * @return The collection of registered sessions.
   */
  public Collection<RaftSession> getSessions() {
    return sessions.values();
  }
}

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
package io.atomix.primitive.service;

import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.serializer.Serializer;
import io.atomix.utils.time.WallClockTimestamp;

/**
 * Base class for user-provided services.
 *
 * @see Commit
 * @see ServiceContext
 * @see ServiceExecutor
 */
public interface PrimitiveService {

  /**
   * Initializes the state machine.
   *
   * @param context The state machine context.
   * @throws NullPointerException if {@code context} is null
   */
  void init(ServiceContext context);

  /**
   * Increments the Raft service time to the given timestamp.
   *
   * @param timestamp the service timestamp
   */
  void tick(WallClockTimestamp timestamp);

  /**
   * Returns the primitive service serializer.
   *
   * @return the primitive service serializer
   */
  Serializer serializer();

  /**
   * Backs up the service state to the given buffer.
   *
   * @param output the buffer to which to back up the service state
   */
  void backup(BackupOutput output);

  /**
   * Restores the service state from the given buffer.
   *
   * @param input the buffer from which to restore the service state
   */
  void restore(BackupInput input);

  /**
   * Applies a commit to the state machine.
   *
   * @param commit the commit to apply
   * @return the commit result
   */
  byte[] apply(Commit<byte[]> commit);

  /**
   * Registers a primitive session.
   *
   * @param session the session to register
   */
  void register(Session session);

  /**
   * Expires the session with the given identifier.
   *
   * @param sessionId the session identifier
   */
  void expire(SessionId sessionId);

  /**
   * Closes the session with the given identifier.
   *
   * @param sessionId the session identifier
   */
  void close(SessionId sessionId);

  /** Closes the state machine. */
  default void close() {}
}

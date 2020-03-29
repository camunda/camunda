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
 * limitations under the License
 */
package io.atomix.primitive.session;

import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.service.PrimitiveService;

/**
 * Support for listening for state changes in server sessions.
 *
 * <p>When implemented by a {@link PrimitiveService StateMachine}, this interface provides support
 * to state machines for reacting to changes in the sessions connected to the cluster. State
 * machines can react to clients {@link #onOpen(Session) registering} and {@link #onClose(Session)
 * unregistering} sessions and servers {@link #onExpire(Session) expiring} sessions.
 *
 * <p>{@link Session}s represent a single client's open connection to a cluster. Within the context
 * of a session, Raft provides additional guarantees for clients like linearizability for writes and
 * sequential consistency for reads. Additionally, state machines can push messages to specific
 * clients via sessions. Typically, all state machines that rely on session-based messaging should
 * implement this interface to track when a session is {@link #onClose(Session) closed}.
 */
public interface SessionListener {

  /**
   * Called when a new session is registered.
   *
   * <p>A session is registered when a new client connects to the cluster or an existing client
   * recovers its session after being partitioned from the cluster. It's important to note that when
   * this method is called, the {@link Session} is <em>not yet open</em> and so events cannot be
   * {@link Session#publish(PrimitiveEvent) published} to the registered session. This is because
   * clients cannot reliably track messages pushed from server state machines to the client until
   * the session has been fully registered. Session event messages may still be published to other
   * already-registered sessions in reaction to a session being registered.
   *
   * <p>To push session event messages to a client through its session upon registration, state
   * machines can use an asynchronous callback or schedule a callback to send a message.
   *
   * <pre>{@code
   * public void onOpen(RaftSession session) {
   *   executor.execute(() -> session.publish("foo", "Hello world!"));
   * }
   *
   * }</pre>
   *
   * Sending a session event message in an asynchronous callback allows the server time to register
   * the session and notify the client before the event message is sent. Published event messages
   * sent via this method will be sent the next time an operation is applied to the state machine.
   *
   * @param session The session that was registered. State machines <em>cannot</em> {@link
   *     Session#publish(PrimitiveEvent)} session events to this session.
   */
  void onOpen(Session session);

  /**
   * Called when a session is expired by the system.
   *
   * <p>This method is called when a client fails to keep its session alive with the cluster. If the
   * leader hasn't heard from a client for a configurable time interval, the leader will expire the
   * session to free the related memory. This method will always be called for a given session
   * before {@link #onClose(Session)}, and {@link #onClose(Session)} will always be called following
   * this method.
   *
   * <p>State machines are free to {@link Session#publish(PrimitiveEvent)} session event messages to
   * any session except the one that expired. Session event messages sent to the session that
   * expired will be lost since the session is closed once this method call completes.
   *
   * @param session The session that was expired. State machines <em>cannot</em> {@link
   *     Session#publish(PrimitiveEvent)} session events to this session.
   */
  void onExpire(Session session);

  /**
   * Called when a session was closed by the client.
   *
   * <p>This method is called when a client explicitly closes a session.
   *
   * <p>State machines are free to {@link Session#publish(PrimitiveEvent)} session event messages to
   * any session except the one that was closed. Session event messages sent to the session that was
   * closed will be lost since the session is closed once this method call completes.
   *
   * @param session The session that was closed. State machines <em>cannot</em> {@link
   *     Session#publish(PrimitiveEvent)} session events to this session.
   */
  void onClose(Session session);
}

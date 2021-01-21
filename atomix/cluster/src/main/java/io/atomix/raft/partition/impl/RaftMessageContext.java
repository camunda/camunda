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
package io.atomix.raft.partition.impl;

/** Protocol message context. */
class RaftMessageContext {

  final String heartbeatSubject;
  final String openSessionSubject;
  final String closeSessionSubject;
  final String keepAliveSubject;
  final String querySubject;
  final String commandSubject;
  final String metadataSubject;
  final String configureSubject;
  final String reconfigureSubject;
  final String installSubject;
  final String transferSubject;
  final String pollSubject;
  final String voteSubject;
  final String appendSubject;
  final String leaderHeartbeatSubject;
  private final String prefix;

  RaftMessageContext(final String prefix) {
    this.prefix = prefix;
    heartbeatSubject = getSubject(prefix, "heartbeat");
    openSessionSubject = getSubject(prefix, "open");
    closeSessionSubject = getSubject(prefix, "close");
    keepAliveSubject = getSubject(prefix, "keep-alive");
    querySubject = getSubject(prefix, "query");
    commandSubject = getSubject(prefix, "command");
    metadataSubject = getSubject(prefix, "metadata");
    configureSubject = getSubject(prefix, "configure");
    reconfigureSubject = getSubject(prefix, "reconfigure");
    installSubject = getSubject(prefix, "install");
    transferSubject = getSubject(prefix, "transfer");
    pollSubject = getSubject(prefix, "poll");
    voteSubject = getSubject(prefix, "vote");
    appendSubject = getSubject(prefix, "append");
    leaderHeartbeatSubject = getSubject(prefix, "leaderHeartbeat");
  }

  private static String getSubject(final String prefix, final String type) {
    if (prefix == null) {
      return type;
    } else {
      return String.format("%s-%s", prefix, type);
    }
  }

  /**
   * Returns the publish subject for the given session.
   *
   * @param sessionId the session for which to return the publish subject
   * @return the publish subject for the given session
   */
  String publishSubject(final long sessionId) {
    if (prefix == null) {
      return String.format("publish-%d", sessionId);
    } else {
      return String.format("%s-publish-%d", prefix, sessionId);
    }
  }

  /**
   * Returns the reset subject for the given session.
   *
   * @param sessionId the session for which to return the reset subject
   * @return the reset subject for the given session
   */
  String resetSubject(final long sessionId) {
    if (prefix == null) {
      return String.format("reset-%d", sessionId);
    } else {
      return String.format("%s-reset-%d", prefix, sessionId);
    }
  }
}

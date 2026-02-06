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
  final String forceConfigureSubject;
  final String joinSubject;
  final String leaveSubject;
  final String installSubject;
  final String transferSubject;
  final String pollSubject;
  final String voteSubject;
  final String appendV1subject;
  final String appendV2subject;
  final String leaderHeartbeatSubject;

  RaftMessageContext(final String prefix) {
    heartbeatSubject = getSubject(prefix, "heartbeat");
    openSessionSubject = getSubject(prefix, "open");
    closeSessionSubject = getSubject(prefix, "close");
    keepAliveSubject = getSubject(prefix, "keep-alive");
    querySubject = getSubject(prefix, "query");
    commandSubject = getSubject(prefix, "command");
    metadataSubject = getSubject(prefix, "metadata");
    configureSubject = getSubject(prefix, "configure");
    reconfigureSubject = getSubject(prefix, "reconfigure");
    forceConfigureSubject = getSubject(prefix, "force-configure");
    joinSubject = getSubject(prefix, "join");
    leaveSubject = getSubject(prefix, "leave");
    installSubject = getSubject(prefix, "install");
    transferSubject = getSubject(prefix, "transfer");
    pollSubject = getSubject(prefix, "poll");
    voteSubject = getSubject(prefix, "vote");
    appendV1subject = getSubject(prefix, "append");
    appendV2subject = getSubject(prefix, "append-versioned");
    leaderHeartbeatSubject = getSubject(prefix, "leaderHeartbeat");
  }

  String getAppendV1subject() {
    return appendV1subject;
  }

  String getAppendV2subject() {
    return appendV2subject;
  }

  String getConfigureSubject() {
    return configureSubject;
  }

  String getForceConfigureSubject() {
    return forceConfigureSubject;
  }

  String getInstallSubject() {
    return installSubject;
  }

  String getJoinSubject() {
    return joinSubject;
  }

  String getLeaveSubject() {
    return leaveSubject;
  }

  String getPollSubject() {
    return pollSubject;
  }

  String getReconfigureSubject() {
    return reconfigureSubject;
  }

  String getVoteSubject() {
    return voteSubject;
  }

  String getTransferSubject() {
    return transferSubject;
  }

  private static String getSubject(final String prefix, final String type) {
    if (prefix == null) {
      return type;
    } else {
      return String.format("%s-%s", prefix, type);
    }
  }
}

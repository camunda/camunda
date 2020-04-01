/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.partition.impl;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.event.PrimitiveEvent;
import io.atomix.primitive.event.impl.DefaultEventType;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.operation.PrimitiveOperation;
import io.atomix.primitive.operation.impl.DefaultOperationId;
import io.atomix.primitive.session.SessionId;
import io.atomix.primitive.session.SessionMetadata;
import io.atomix.raft.RaftError;
import io.atomix.raft.ReadConsistency;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.CloseSessionRequest;
import io.atomix.raft.protocol.CloseSessionResponse;
import io.atomix.raft.protocol.CommandRequest;
import io.atomix.raft.protocol.CommandResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.HeartbeatRequest;
import io.atomix.raft.protocol.HeartbeatResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.JoinRequest;
import io.atomix.raft.protocol.JoinResponse;
import io.atomix.raft.protocol.KeepAliveRequest;
import io.atomix.raft.protocol.KeepAliveResponse;
import io.atomix.raft.protocol.LeaveRequest;
import io.atomix.raft.protocol.LeaveResponse;
import io.atomix.raft.protocol.MetadataRequest;
import io.atomix.raft.protocol.MetadataResponse;
import io.atomix.raft.protocol.OpenSessionRequest;
import io.atomix.raft.protocol.OpenSessionResponse;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.PublishRequest;
import io.atomix.raft.protocol.QueryRequest;
import io.atomix.raft.protocol.QueryResponse;
import io.atomix.raft.protocol.RaftResponse;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.ResetRequest;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.raft.storage.log.entry.CloseSessionEntry;
import io.atomix.raft.storage.log.entry.CommandEntry;
import io.atomix.raft.storage.log.entry.ConfigurationEntry;
import io.atomix.raft.storage.log.entry.InitializeEntry;
import io.atomix.raft.storage.log.entry.KeepAliveEntry;
import io.atomix.raft.storage.log.entry.MetadataEntry;
import io.atomix.raft.storage.log.entry.OpenSessionEntry;
import io.atomix.raft.storage.log.entry.QueryEntry;
import io.atomix.raft.storage.system.Configuration;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespaces;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

/** Storage serializer namespaces. */
public final class RaftNamespaces {

  /** Raft protocol namespace. */
  public static final Namespace RAFT_PROTOCOL =
      Namespace.builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
          .register(OpenSessionRequest.class)
          .register(OpenSessionResponse.class)
          .register(CloseSessionRequest.class)
          .register(CloseSessionResponse.class)
          .register(KeepAliveRequest.class)
          .register(KeepAliveResponse.class)
          .register(HeartbeatRequest.class)
          .register(HeartbeatResponse.class)
          .register(QueryRequest.class)
          .register(QueryResponse.class)
          .register(CommandRequest.class)
          .register(CommandResponse.class)
          .register(MetadataRequest.class)
          .register(MetadataResponse.class)
          .register(JoinRequest.class)
          .register(JoinResponse.class)
          .register(LeaveRequest.class)
          .register(LeaveResponse.class)
          .register(ConfigureRequest.class)
          .register(ConfigureResponse.class)
          .register(ReconfigureRequest.class)
          .register(ReconfigureResponse.class)
          .register(InstallRequest.class)
          .register(InstallResponse.class)
          .register(PollRequest.class)
          .register(PollResponse.class)
          .register(VoteRequest.class)
          .register(VoteResponse.class)
          .register(AppendRequest.class)
          .register(AppendResponse.class)
          .register(PublishRequest.class)
          .register(ResetRequest.class)
          .register(RaftResponse.Status.class)
          .register(RaftError.class)
          .register(RaftError.Type.class)
          .register(ReadConsistency.class)
          .register(SessionMetadata.class)
          .register(CloseSessionEntry.class)
          .register(CommandEntry.class)
          .register(ConfigurationEntry.class)
          .register(InitializeEntry.class)
          .register(KeepAliveEntry.class)
          .register(MetadataEntry.class)
          .register(OpenSessionEntry.class)
          .register(QueryEntry.class)
          .register(PrimitiveOperation.class)
          .register(PrimitiveEvent.class)
          .register(DefaultEventType.class)
          .register(DefaultOperationId.class)
          .register(OperationType.class)
          .register(ReadConsistency.class)
          .register(ArrayList.class)
          .register(LinkedList.class)
          .register(Collections.emptyList().getClass())
          .register(HashSet.class)
          .register(DefaultRaftMember.class)
          .register(MemberId.class)
          .register(SessionId.class)
          .register(RaftMember.Type.class)
          .register(Instant.class)
          .register(Configuration.class)
          .register(ZeebeEntry.class)
          .build("RaftProtocol");

  /** Raft storage namespace. */
  public static final Namespace RAFT_STORAGE =
      Namespace.builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID + 100)
          .register(CloseSessionEntry.class)
          .register(CommandEntry.class)
          .register(ConfigurationEntry.class)
          .register(InitializeEntry.class)
          .register(KeepAliveEntry.class)
          .register(MetadataEntry.class)
          .register(OpenSessionEntry.class)
          .register(QueryEntry.class)
          .register(PrimitiveOperation.class)
          .register(DefaultOperationId.class)
          .register(OperationType.class)
          .register(ReadConsistency.class)
          .register(ArrayList.class)
          .register(HashSet.class)
          .register(DefaultRaftMember.class)
          .register(MemberId.class)
          .register(RaftMember.Type.class)
          .register(Instant.class)
          .register(Configuration.class)
          .register(ZeebeEntry.class)
          .build("RaftStorage");

  private RaftNamespaces() {}
}

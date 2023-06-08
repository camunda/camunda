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
import io.atomix.raft.RaftError;
import io.atomix.raft.RaftError.Type;
import io.atomix.raft.cluster.RaftMember;
import io.atomix.raft.cluster.impl.DefaultRaftMember;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendRequestV2;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.protocol.ConfigureRequest;
import io.atomix.raft.protocol.ConfigureResponse;
import io.atomix.raft.protocol.InstallRequest;
import io.atomix.raft.protocol.InstallResponse;
import io.atomix.raft.protocol.PersistedRaftRecord;
import io.atomix.raft.protocol.PollRequest;
import io.atomix.raft.protocol.PollResponse;
import io.atomix.raft.protocol.RaftResponse.Status;
import io.atomix.raft.protocol.ReconfigureRequest;
import io.atomix.raft.protocol.ReconfigureResponse;
import io.atomix.raft.protocol.ReplicatedJournalRecord;
import io.atomix.raft.protocol.TransferRequest;
import io.atomix.raft.protocol.TransferResponse;
import io.atomix.raft.protocol.VoteRequest;
import io.atomix.raft.protocol.VoteResponse;
import io.atomix.utils.serializer.Namespace;
import io.atomix.utils.serializer.Namespace.Builder;
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
      new Builder()
          .register(Namespaces.BASIC)
          .nextId(Namespaces.BEGIN_USER_CUSTOM_ID)
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
          .register(Status.class)
          .register(RaftError.class)
          .register(Type.class)
          .nextId(517) // for backwards compatibility
          .register(ArrayList.class)
          .register(LinkedList.class)
          .register(Collections.emptyList().getClass())
          .register(HashSet.class)
          .register(DefaultRaftMember.class)
          .register(MemberId.class)
          .register(RaftMember.Type.class)
          .register(Instant.class)
          .nextId(528) // for backwards compatibility
          .register(PersistedRaftRecord.class)
          .register(TransferRequest.class)
          .register(TransferResponse.class)
          .register(AppendRequestV2.class)
          .register(ReplicatedJournalRecord.class)
          .name("RaftProtocol")
          .build();

  private RaftNamespaces() {}
}

/*
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
package io.atomix.raft.cluster;

import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.storage.system.Configuration;
import org.jspecify.annotations.Nullable;

/**
 * The local state of a replica, for the reports of a recovery (design 5.6.3, step 1), see {@link
 * io.atomix.raft.RaftServer#getReplicaState()}.
 *
 * @param configuration the current configuration with the configuration term, or null if the
 *     replica has none (it started without data and was not added yet)
 * @param incarnation the incarnation of the local member
 * @param lastLogTerm the term of the last log entry, 0 if the log is empty
 * @param lastLogIndex the index of the last log entry, 0 if the log is empty
 * @param commitIndex the commit index
 * @param role the role
 * @param fenced true if the local member found out that it is fenced
 */
public record ReplicaState(
    @Nullable Configuration configuration,
    long incarnation,
    long lastLogTerm,
    long lastLogIndex,
    long commitIndex,
    Role role,
    boolean fenced) {}

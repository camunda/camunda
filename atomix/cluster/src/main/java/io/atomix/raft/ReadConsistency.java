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
package io.atomix.raft;

/**
 * Constants for specifying Raft {@link io.atomix.primitive.operation.OperationType#QUERY query}
 * consistency levels.
 *
 * <p>This enum provides identifiers for configuring consistency levels for {@link
 * io.atomix.primitive.operation.OperationType#QUERY queries} submitted to a Raft cluster.
 *
 * <p>Consistency levels are used to dictate how queries are routed through the Raft cluster and the
 * requirements for completing read operations based on submitted queries. For expectations of
 * specific consistency levels, see below.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public enum ReadConsistency {

  /**
   * Enforces sequential query consistency.
   *
   * <p>Sequential read consistency requires that clients always see state progress in monotonically
   * increasing order. Note that this constraint allows reads from followers. When a sequential
   * {@link io.atomix.primitive.operation.OperationType#QUERY query} is submitted to the cluster,
   * the first server that receives the query will handle it. However, in order to ensure that state
   * does not go back in time, the client must submit its last known index with the query as well.
   * If the server that receives the query has not advanced past the provided client index, it will
   * queue the query and await more entries from the leader.
   */
  SEQUENTIAL,

  /**
   * Enforces linearizable query consistency based on leader lease.
   *
   * <p>Bounded linearizability is a special implementation of linearizable reads that relies on the
   * semantics of Raft's election timers to determine whether it is safe to immediately apply a
   * query to the Raft state machine. When a linearizable {@link
   * io.atomix.primitive.operation.OperationType#QUERY query} is submitted to the Raft cluster with
   * linearizable consistency, it must be forwarded to the current cluster leader. For lease-based
   * linearizability, the leader will determine whether it's safe to apply the query to its state
   * machine based on the last time it successfully contacted a majority of the cluster. If the
   * leader contacted a majority of the cluster within the last election timeout, it assumes that no
   * other member could have since become the leader and immediately applies the query to its state
   * machine. Alternatively, if it hasn't contacted a majority of the cluster within an election
   * timeout, the leader will handle the query as if it were submitted with {@link #LINEARIZABLE}
   * consistency.
   */
  LINEARIZABLE_LEASE,

  /**
   * Enforces linearizable query consistency.
   *
   * <p>The linearizable consistency level guarantees consistency by contacting a majority of the
   * cluster on every read. When a {@link io.atomix.primitive.operation.OperationType#QUERY query}
   * is submitted to the cluster with linearizable consistency, it must be forwarded to the current
   * cluster leader. Once received by the leader, the leader will contact a majority of the cluster
   * before applying the query to its state machine and returning the result. Note that if the
   * leader is already in the process of contacting a majority of the cluster, it will queue the
   * {@link io.atomix.primitive.operation.OperationType#QUERY query} to be processed on the next
   * round trip. This allows the leader to batch expensive quorum based reads for efficiency.
   */
  LINEARIZABLE
}

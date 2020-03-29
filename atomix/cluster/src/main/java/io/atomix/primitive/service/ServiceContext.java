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

import io.atomix.cluster.MemberId;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.operation.OperationType;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.Sessions;
import io.atomix.utils.time.LogicalClock;
import io.atomix.utils.time.WallClock;

/**
 * State machine context.
 *
 * <p>The context is reflective of the current position and state of the Raft state machine. In
 * particular, it exposes the current approximate {@link ServiceContext#wallClock() time} and all
 * open {@link Sessions}.
 */
public interface ServiceContext {

  /**
   * Returns the state machine identifier.
   *
   * @return The unique state machine identifier.
   */
  PrimitiveId serviceId();

  /**
   * Returns the state machine name.
   *
   * @return The state machine name.
   */
  String serviceName();

  /**
   * Returns the state machine type.
   *
   * @return The state machine type.
   */
  PrimitiveType serviceType();

  /**
   * Returns the local member ID
   *
   * @return The local member ID
   */
  MemberId localMemberId();

  /**
   * Returns the service configuration.
   *
   * @param <C> the configuration type
   * @return the service configuration
   */
  <C extends ServiceConfig> C serviceConfig();

  /**
   * Returns the current state machine index.
   *
   * <p>The state index is indicative of the index of the current operation being applied to the
   * server state machine. If a query is being applied, the index of the last command applied will
   * be used.
   *
   * @return The current state machine index.
   */
  long currentIndex();

  /**
   * Returns the current session.
   *
   * @return the current session
   */
  Session currentSession();

  /**
   * Returns the current operation type.
   *
   * @return the current operation type
   */
  OperationType currentOperation();

  /**
   * Returns the state machine's logical clock.
   *
   * @return The state machine's logical clock.
   */
  LogicalClock logicalClock();

  /**
   * Returns the state machine's wall clock.
   *
   * @return The state machine's wall clock.
   */
  WallClock wallClock();
}

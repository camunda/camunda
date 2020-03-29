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
package io.atomix.raft.cluster;

import io.atomix.utils.event.AbstractEvent;

/** Raft cluster event. */
public class RaftClusterEvent extends AbstractEvent<RaftClusterEvent.Type, RaftMember> {

  public RaftClusterEvent(final Type type, final RaftMember subject) {
    super(type, subject);
  }

  public RaftClusterEvent(final Type type, final RaftMember subject, final long time) {
    super(type, subject, time);
  }

  /** Raft cluster event type. */
  public enum Type {
    JOIN,
    LEAVE,
  }
}

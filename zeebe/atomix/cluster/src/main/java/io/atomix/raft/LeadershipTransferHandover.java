/*
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
package io.atomix.raft;

/**
 * Broker-supplied step the leader runs during a coordinated leadership transfer, once the desired
 * leader has caught up and just before it is promoted. It lets the broker pass on state the new
 * leader would otherwise have to rebuild after taking over.
 *
 * <p>The handover is best effort: the leader promotes the desired leader straight after starting
 * it, without waiting for it to complete.
 */
@FunctionalInterface
public interface LeadershipTransferHandover {

  /** Handover for a server with no broker attached (e.g. Raft-only tests): nothing to pass on. */
  LeadershipTransferHandover NONE = () -> {};

  /** Starts the handover. Called on the Raft thread, so it must not block. */
  void handOver();
}

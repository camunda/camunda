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

import io.atomix.raft.protocol.ExporterPosition;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Broker-supplied exporter positions carried on TimeoutNow during a coordinated leadership
 * transfer. The leader attaches its exporters' positions to every TimeoutNow it sends, and the
 * desired leader receives them before it starts its election, so it can resume exporting from close
 * to where the old leader left off instead of from the last periodic distribution.
 *
 * <p>The handover is best effort: a failing hook is ignored and the transfer carries on as if there
 * were nothing to hand over.
 */
@NullMarked
public interface LeadershipTransferHandover {

  /** Handover for a server with no broker attached (e.g. Raft-only tests): nothing to pass on. */
  LeadershipTransferHandover NONE =
      new LeadershipTransferHandover() {
        @Override
        public List<ExporterPosition> exporterPositions() {
          return List.of();
        }

        @Override
        public void receive(final long term, final List<ExporterPosition> exporterPositions) {}
      };

  /** Leader, Raft thread, non-blocking: the exporter positions to attach to the next TimeoutNow. */
  List<ExporterPosition> exporterPositions();

  /**
   * Follower, Raft thread, non-blocking: the exporter positions from an accepted TimeoutNow, called
   * before the follower starts its election. {@code term} is the sending leader's term.
   */
  void receive(long term, List<ExporterPosition> exporterPositions);
}

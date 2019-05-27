/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.logstreams.restore;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.Partition;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.util.ZbLogger;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Iterates over partition members cyclically, skipping the local node. After each loop, it will
 * refresh the list of members it should use.
 */
public class CyclicPartitionNodeProvider implements RestoreNodeProvider {
  private final Supplier<Partition> partitionSupplier;
  private final String localMemberId;
  private final Queue<MemberId> members;
  private final Logger logger;

  public CyclicPartitionNodeProvider(Supplier<Partition> partitionSupplier, String localMemberId) {
    this.partitionSupplier = partitionSupplier;
    this.localMemberId = localMemberId;
    this.members = new ArrayDeque<>();
    this.logger =
        new ZbLogger(
            String.format(
                "%s-%s", CyclicPartitionNodeProvider.class.getCanonicalName(), localMemberId));
  }

  @Override
  public MemberId provideRestoreNode() {
    return memberQueue().poll();
  }

  private Queue<MemberId> memberQueue() {
    if (members.isEmpty()) {
      final Partition partition = partitionSupplier.get();
      if (partition != null) {
        partition.members().stream()
            .filter(m -> !m.id().equals(localMemberId))
            .forEach(members::add);
      } else {
        logger.warn("No partition provided, cannot provide a restore node");
      }
    }

    return members;
  }
}

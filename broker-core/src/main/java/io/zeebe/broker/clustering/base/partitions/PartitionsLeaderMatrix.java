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
package io.zeebe.broker.clustering.base.partitions;

import io.zeebe.broker.Loggers;
import io.zeebe.protocol.Protocol;
import org.agrona.collections.IntArrayList;
import org.slf4j.Logger;

public class PartitionsLeaderMatrix {

  public static final int LEADER = 1;
  public static final int FOLLOWER = 2;
  public static final Logger LOG = Loggers.CLUSTERING_LOGGER;

  final int[][] matrix;

  private final int rowCount;
  private final int columnCount;
  private final int replicationFactor;

  public PartitionsLeaderMatrix(
      final int partitionsCount, final int clusterSize, final int replicationFactor) {
    ensureValidValues(partitionsCount, clusterSize, replicationFactor);

    this.rowCount = partitionsCount;
    this.columnCount = clusterSize;

    matrix = new int[rowCount][columnCount];

    this.replicationFactor = replicationFactor;
    init();

    printMatrix();
  }

  private void printMatrix() {
    if (LOG.isTraceEnabled()) {
      final StringBuilder builder = new StringBuilder("\nPartitionsmatrix:\n");
      builder.append("\t|");
      for (int column = 0; column < columnCount; column++) {
        builder.append("\t").append(column).append("|");
      }
      builder.append("\n");

      for (int row = 0; row < rowCount; row++) {
        builder.append(row).append("\t|");
        for (int column = 0; column < columnCount; column++) {
          final int value = this.matrix[row][column];

          builder.append("\t");
          if (value == LEADER) {
            builder.append("L");
          } else if (value == FOLLOWER) {
            builder.append("F");
          } else {
            builder.append("-");
          }
          builder.append("|");
        }
        builder.append("\n");
      }
      LOG.trace(builder.toString());
    }
  }

  private void ensureValidValues(
      final int partitionsCount, final int clusterSize, final int replicationFactor) {
    ensureLargerThen(partitionsCount, 1, "Partitions count must not be smaller then one.");
    ensureSmallerThen(
        partitionsCount,
        Protocol.MAXIMUM_PARTITIONS,
        "Partitions count must be smaller then maximum partition space of "
            + Protocol.MAXIMUM_PARTITIONS
            + ".");
    ensureLargerThen(clusterSize, 1, "Cluster size must not be smaller then one.");
    ensureLargerThen(replicationFactor, 1, "Replication factor must not be smaller then one.");

    ensureLargerThen(
        clusterSize,
        replicationFactor,
        "Cluster size must not be smaller then replication factor.");
  }

  private void ensureLargerThen(final int partitionsCount, final int i, final String s) {
    if (partitionsCount < i) {
      throw new IllegalArgumentException(s);
    }
  }

  private void ensureSmallerThen(final int partitionsCount, final long i, final String s) {
    if (partitionsCount >= i) {
      throw new IllegalArgumentException(s);
    }
  }

  private void init() {
    for (int row = 0; row < rowCount; row++) {
      final int leader = row % columnCount;
      matrix[row][leader] = LEADER;

      int column;
      for (int i = 1; i < replicationFactor; i++) {
        column = (leader + i) % columnCount;
        matrix[row][column] = FOLLOWER;
      }
    }
  }

  public IntArrayList getLeadingPartitions(final int nodeId) {
    final IntArrayList leadingPartitions = new IntArrayList();
    for (int row = 0; row < rowCount; row++) {
      final int state = this.matrix[row][nodeId];
      if (LEADER == state) {
        leadingPartitions.add(row);
      }
    }
    return leadingPartitions;
  }

  public IntArrayList getFollowingPartitions(final int nodeId) {

    final IntArrayList followingPartitions = new IntArrayList();
    for (int row = 0; row < rowCount; row++) {
      final int state = this.matrix[row][nodeId];
      if (FOLLOWER == state) {
        followingPartitions.add(row);
      }
    }
    return followingPartitions;
  }

  public IntArrayList getMembersForPartition(final int nodeId, final int partitionId) {
    final IntArrayList members = new IntArrayList();
    for (int column = 0; column < columnCount; column++) {
      if (column != nodeId) {
        final int state = this.matrix[partitionId][column];
        if (state > 0) {
          members.add(column);
        }
      }
    }
    return members;
  }
}

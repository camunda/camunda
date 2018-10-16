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

import static io.zeebe.broker.clustering.base.partitions.PartitionsLeaderMatrix.FOLLOWER;
import static io.zeebe.broker.clustering.base.partitions.PartitionsLeaderMatrix.LEADER;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.protocol.Protocol;
import org.agrona.collections.IntArrayList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PartitionsLeaderMatrixTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void shouldCreateDefaultPartitionsMatrix() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(1, 1, 1);

    // then
    final int[][] matrix = partitionsLeaderMatrix.matrix;
    assertThat(matrix).hasSize(1);
    assertThat(matrix[0]).hasSize(1);
    assertThat(matrix).containsExactly(new int[] {LEADER});
  }

  @Test
  public void shouldCreatePartitionsMatrixWithMorePartitionsThenNodes() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(2, 1, 1);

    // then
    final int[][] matrix = partitionsLeaderMatrix.matrix;

    assertThat(matrix).hasSize(2);
    assertThat(matrix[0]).hasSize(1);
    assertThat(matrix[1]).hasSize(1);

    assertThat(matrix[0]).containsExactly(LEADER);
    assertThat(matrix[1]).containsExactly(LEADER);
  }

  @Test
  public void shouldCreatePartitionsMatrixWithMoreNodesThenPartitions() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(1, 2, 1);

    // then
    final int[][] matrix = partitionsLeaderMatrix.matrix;

    assertThat(matrix).hasSize(1);
    assertThat(matrix[0]).hasSize(2);

    assertThat(matrix[0]).containsExactly(LEADER, 0);
  }

  @Test
  public void shouldCreatePartitionsMatrixWithMoreNodesThenPartitionsAndReplicationFactor() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(1, 2, 2);

    // then
    final int[][] matrix = partitionsLeaderMatrix.matrix;

    assertThat(matrix).hasSize(1);
    assertThat(matrix[0]).hasSize(2);

    assertThat(matrix[0]).containsExactly(LEADER, FOLLOWER);
  }

  @Test
  public void shouldCreatePartitionsWithMorePartitionsAndNodes() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(6, 5, 3);

    // then
    final int[][] matrix = partitionsLeaderMatrix.matrix;

    assertThat(matrix).hasSize(6);
    assertThat(matrix).extracting(v -> v.length).containsOnly(5);

    assertThat(matrix[0]).containsExactly(LEADER, FOLLOWER, FOLLOWER, 0, 0);
    assertThat(matrix[1]).containsExactly(0, LEADER, FOLLOWER, FOLLOWER, 0);
    assertThat(matrix[2]).containsExactly(0, 0, LEADER, FOLLOWER, FOLLOWER);
    assertThat(matrix[3]).containsExactly(FOLLOWER, 0, 0, LEADER, FOLLOWER);
    assertThat(matrix[4]).containsExactly(FOLLOWER, FOLLOWER, 0, 0, LEADER);
    assertThat(matrix[5]).containsExactly(LEADER, FOLLOWER, FOLLOWER, 0, 0);
  }

  @Test
  public void shouldReturnLeadingPartitionsForNodeId() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(6, 5, 3);

    // when
    final IntArrayList leadingPartitions = partitionsLeaderMatrix.getLeadingPartitions(0);

    // then
    assertThat(leadingPartitions).containsExactly(0, 5);
  }

  @Test
  public void shouldReturnFollowingPartitionsForNodeId() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(6, 5, 3);

    // when
    final IntArrayList followingPartitions = partitionsLeaderMatrix.getFollowingPartitions(1);

    // then
    assertThat(followingPartitions).containsExactly(0, 4, 5);
  }

  @Test
  public void shouldReturnMembersForPartition() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(6, 5, 3);

    // when
    final IntArrayList membersForPartition = partitionsLeaderMatrix.getMembersForPartition(0, 1);

    // then
    assertThat(membersForPartition).containsExactly(1, 2, 3);
  }

  @Test
  public void shouldReturnMembersForPartitionWithoutSelf() {
    // given
    final PartitionsLeaderMatrix partitionsLeaderMatrix = new PartitionsLeaderMatrix(6, 5, 3);

    // when
    final IntArrayList membersForPartition = partitionsLeaderMatrix.getMembersForPartition(0, 5);

    // then
    assertThat(membersForPartition).containsExactly(1, 2);
  }

  @Test
  public void shouldThrowExceptionOnZeroPartitionsCount() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Partitions count must not be smaller then one.");

    // when
    new PartitionsLeaderMatrix(0, 1, 1);
  }

  @Test
  public void shouldThrowExceptionOnNegativePartitionsCount() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Partitions count must not be smaller then one.");

    // when
    new PartitionsLeaderMatrix(-1, 1, 1);
  }

  @Test
  public void shouldThrowExceptionToLargePartitionsCount() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
        "Partitions count must be smaller then maximum partition space of "
            + Protocol.MAXIMUM_PARTITIONS
            + ".");

    // when
    new PartitionsLeaderMatrix((int) Protocol.MAXIMUM_PARTITIONS, 1, 1);
  }

  @Test
  public void shouldThrowExceptionOnZeroClusterSize() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Cluster size must not be smaller then one.");

    // when
    new PartitionsLeaderMatrix(1, 0, 1);
  }

  @Test
  public void shouldThrowExceptionOnNegativeClusterSize() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Cluster size must not be smaller then one.");

    // when
    new PartitionsLeaderMatrix(1, -1, 1);
  }

  @Test
  public void shouldThrowExceptionOnZeroReplicationFactor() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Replication factor must not be smaller then one.");

    // when
    new PartitionsLeaderMatrix(1, 1, -1);
  }

  @Test
  public void shouldThrowExceptionOnNegativeReplicationFactor() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Replication factor must not be smaller then one.");

    // when
    new PartitionsLeaderMatrix(1, 1, -1);
  }

  @Test
  public void shouldThrowExceptionOnSmallerClusterSizeThenReplicationFactor() {
    // expect
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Cluster size must not be smaller then replication factor.");

    // when
    new PartitionsLeaderMatrix(1, 1, 2);
  }
}

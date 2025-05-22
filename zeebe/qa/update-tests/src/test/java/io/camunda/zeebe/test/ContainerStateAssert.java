/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test;

import static io.camunda.zeebe.test.UpdateTestCaseProvider.PROCESS_ID;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.qa.util.actuator.PartitionsActuator.PartitionStatus;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.AbstractObjectAssert;

@SuppressWarnings("UnusedReturnValue")
final class ContainerStateAssert
    extends AbstractObjectAssert<ContainerStateAssert, ContainerState> {

  public ContainerStateAssert(final ContainerState actual) {
    super(actual, ContainerStateAssert.class);
  }

  public static ContainerStateAssert assertThat(final ContainerState actual) {
    return new ContainerStateAssert(actual);
  }

  public ContainerStateAssert hasCompletedProcess(final String processId) {
    final boolean isCompleted = actual.hasElementInState(PROCESS_ID, "ELEMENT_COMPLETED");
    if (!isCompleted) {
      failWithMessage("expected process %s to be completed, but was not", processId);
    }

    return myself;
  }

  public ContainerStateAssert eventuallyHasCompletedProcess(final String processId) {
    await("has completed process " + processId)
        .atMost(Duration.ofSeconds(10))
        .pollInterval(Duration.ofMillis(250))
        .untilAsserted(() -> hasCompletedProcess(processId));
    return myself;
  }

  @SuppressWarnings("ConstantConditions")
  public ContainerStateAssert hasSnapshotAvailable(final int partitionId) {
    final Map<Integer, PartitionStatus> partitions = actual.getPartitionsActuator().query();
    final PartitionStatus partitionStatus = partitions.get(partitionId);
    if (partitionStatus == null) {
      failWithMessage(
          "expected partitions query to return info about partition %d, but got %s",
          partitionId, partitions.keySet());
    }

    // the IDE is unaware that if null, failWithMessage will throw, and we won't reach here, so
    // disable the warning
    if (partitionStatus.snapshotId() == null || partitionStatus.snapshotId().isBlank()) {
      failWithMessage("expected to have a snapshot, but got nothing");
    }

    return myself;
  }

  public ContainerStateAssert eventuallyHasSnapshotAvailable(final int partitionId) {
    await("has snapshot available on partition " + partitionId)
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(() -> hasSnapshotAvailable(partitionId));
    return myself;
  }

  @SuppressWarnings("ConstantConditions")
  public ContainerStateAssert hasNoSnapshotAvailable(final int partitionId) {
    final Map<Integer, PartitionStatus> partitions = actual.getPartitionsActuator().query();
    final PartitionStatus partitionStatus = partitions.get(partitionId);
    if (partitionStatus == null) {
      failWithMessage(
          "expected partitions query to return info about partition %d, but got %s",
          partitionId, partitions.keySet());
    }

    // the IDE is unaware that if null, failWithMessage will throw and we won't reach here, so
    // disable the warning
    if (partitionStatus.snapshotId() != null && !partitionStatus.snapshotId().isBlank()) {
      failWithMessage("expected to have no snapshot, but got %s", partitionStatus.snapshotId());
    }

    return myself;
  }

  public void hasNewSnapshotAvailable(final int partitionId, final String snapshotId) {
    final var partitions = actual.getPartitionsActuator().query();
    final PartitionStatus partitionStatus = partitions.get(partitionId);
    if (partitionStatus == null) {
      failWithMessage(
          "expected partitions query to return info about partition %d, but got %s",
          partitionId, partitions.keySet());
    }
    final var newSnapshotId = FileBasedSnapshotId.ofFileName(partitionStatus.snapshotId());

    final var oldSnapshotId = FileBasedSnapshotId.ofFileName(snapshotId);

    org.assertj.core.api.Assertions.assertThat(oldSnapshotId)
        .isPresent()
        .hasValueSatisfying(
            oldId ->
                org.assertj.core.api.Assertions.assertThat(newSnapshotId)
                    .isPresent()
                    .hasValueSatisfying(
                        newId ->
                            org.assertj.core.api.Assertions.assertThat(oldId.getProcessedPosition())
                                .isLessThan(newId.getProcessedPosition())));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.backup;

import static io.camunda.optimize.service.metadata.Version.VERSION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.util.SnapshotUtil;
import org.junit.jupiter.api.Test;

public class SnapshotUtilTest {
  @Test
  public void getSnapshotNameForImportIndices() {
    // when/then
    assertThat(SnapshotUtil.getSnapshotNameForImportIndices(123L))
        .isEqualTo(String.format("camunda_optimize_123_%s_part_1_of_2", VERSION));
  }

  @Test
  public void getSnapshotNameForNonImportIndices() {
    // when/then
    assertThat(SnapshotUtil.getSnapshotNameForNonImportIndices(123L))
        .isEqualTo(String.format("camunda_optimize_123_%s_part_2_of_2", VERSION));
  }

  @Test
  public void getSnapshotPrefixWithBackupId() {
    // when/then
    assertThat(SnapshotUtil.getSnapshotPrefixWithBackupId(123L)).isEqualTo("camunda_optimize_123_");
  }

  @Test
  public void getBackupIdFromSnapshotName() {
    // when/then
    assertThat(SnapshotUtil.getBackupIdFromSnapshotName("camunda_optimize_123_3.9.0_part_1_of_2"))
        .isEqualTo(
            SnapshotUtil.getBackupIdFromSnapshotName("camunda_optimize_123_3.9.0_part_2_of_2"))
        .isEqualTo(123L);
  }
}

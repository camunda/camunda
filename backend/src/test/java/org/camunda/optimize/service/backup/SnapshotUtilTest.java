/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.backup;

import org.camunda.optimize.service.util.SnapshotUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.metadata.Version.VERSION;

public class SnapshotUtilTest {
  @Test
  public void getSnapshotNameForImportIndices() {
    // when/then
    assertThat(SnapshotUtil.getSnapshotNameForImportIndices(123))
      .isEqualTo(String.format("camunda_optimize_123_%s_part_1_of_2", VERSION));
  }

  @Test
  public void getSnapshotNameForNonImportIndices() {
    // when/then
    assertThat(SnapshotUtil.getSnapshotNameForNonImportIndices(123))
      .isEqualTo(String.format("camunda_optimize_123_%s_part_2_of_2", VERSION));
  }

  @Test
  public void getSnapshotPrefixWithBackupId() {
    // when/then
    assertThat(SnapshotUtil.getSnapshotPrefixWithBackupId(123))
      .isEqualTo("camunda_optimize_123_");
  }

  @Test
  public void getBackupIdFromSnapshotName() {
    // when/then
    assertThat(SnapshotUtil.getBackupIdFromSnapshotName("camunda_optimize_123_3.9.0_part_1_of_2"))
      .isEqualTo(SnapshotUtil.getBackupIdFromSnapshotName("camunda_optimize_123_3.9.0_part_2_of_2"))
      .isEqualTo(123);
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.webapps.backup.Metadata;
import org.junit.jupiter.api.Test;

public class WebappsSnapshotNameProviderTest {
  private final SnapshotNameProvider snapshotNameProvider = new WebappsSnapshotNameProvider();
  private final Metadata metadata = new Metadata(12312938123L, "8.7.3", 11, 13);

  @Test
  void shouldBuildAndExtractSnapshotName() {
    final var snapshotName = snapshotNameProvider.getSnapshotName(metadata);
    assertThat(snapshotNameProvider.extractMetadataFromSnapshotName(snapshotName))
        .isEqualTo(metadata);
    assertThat(snapshotNameProvider.extractBackupId(snapshotName)).isEqualTo(metadata.backupId());
  }

  @Test
  void shouldBuildAndExtractTenantPrefixedSnapshotName() {
    final SnapshotNameProvider physicalTenantProvider = new WebappsSnapshotNameProvider("tenant1");

    final var snapshotName = physicalTenantProvider.getSnapshotName(metadata);

    assertThat(snapshotName).startsWith("tenant1_camunda_webapps_");
    assertThat(physicalTenantProvider.extractMetadataFromSnapshotName(snapshotName))
        .isEqualTo(metadata);
    assertThat(physicalTenantProvider.extractBackupId(snapshotName)).isEqualTo(metadata.backupId());
  }

  @Test
  void shouldNotExtractMetadataFromAnotherTenantsSnapshotName() {
    final SnapshotNameProvider physicalTenantProvider = new WebappsSnapshotNameProvider("tenant1");
    final var snapshotName = physicalTenantProvider.getSnapshotName(metadata);

    assertThatThrownBy(() -> snapshotNameProvider.extractMetadataFromSnapshotName(snapshotName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldKeepDefaultNamesUnprefixed() {
    assertThat(snapshotNameProvider.getSnapshotName(metadata))
        .isEqualTo("camunda_webapps_12312938123_8.7.3_part_11_of_13");
    assertThat(snapshotNameProvider.snapshotNamePrefix()).isEqualTo("camunda_webapps_");
  }
}

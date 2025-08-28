/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.time.Duration;
import java.util.Set;

public class Data {
  private static final String PREFIX = "camunda.data";
  private static final Set<String> LEGACY_SNAPSHOT_PERIOD_PROPERTIES =
      Set.of("zeebe.broker.data.snapshotPeriod");

  /** How often we take snapshots of streams (time unit) */
  private Duration snapshotPeriod = Duration.ofMinutes(5);

  /** This section allows to configure primary Zeebe's data storage. */
  private PrimaryStorage primaryStorage = new PrimaryStorage();

  /** This section allows configuring a backup store. */
  private Backup backup = new Backup();

  /** This section allows configuring export. */
  private Export export = new Export();

  /** This section allows to configure Zeebe's secondary storage. */
  private SecondaryStorage secondaryStorage = new SecondaryStorage();

  public Duration getSnapshotPeriod() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".snapshot-period",
        snapshotPeriod,
        Duration.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_SNAPSHOT_PERIOD_PROPERTIES);
  }

  public void setSnapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public PrimaryStorage getPrimaryStorage() {
    return primaryStorage;
  }

  public void setPrimaryStorage(final PrimaryStorage primaryStorage) {
    this.primaryStorage = primaryStorage;
  }

  public Backup getBackup() {
    return backup;
  }

  public void setBackup(final Backup backup) {
    this.backup = backup;
  }

  public Export getExport() {
    return export;
  }

  public void setExport(final Export export) {
    this.export = export;
  }

  public SecondaryStorage getSecondaryStorage() {
    return secondaryStorage;
  }

  public void setSecondaryStorage(final SecondaryStorage secondaryStorage) {
    this.secondaryStorage = secondaryStorage;
  }
}

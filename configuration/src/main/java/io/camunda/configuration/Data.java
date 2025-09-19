/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Data {

  /** How often we take snapshots of streams (time unit) */
  private Duration snapshotPeriod = Duration.ofMinutes(5);

  /** This section allows to configure primary Zeebe's data storage. */
  @NestedConfigurationProperty private PrimaryStorage primaryStorage = new PrimaryStorage();

  /** This section allows configuring a backup store. */
  @NestedConfigurationProperty private Backup backup = new Backup();

  /** This section allows configuring export. */
  @NestedConfigurationProperty private Export export = new Export();

  /** This section allows to configure Zeebe's secondary storage. */
  @NestedConfigurationProperty private SecondaryStorage secondaryStorage = new SecondaryStorage();

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
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

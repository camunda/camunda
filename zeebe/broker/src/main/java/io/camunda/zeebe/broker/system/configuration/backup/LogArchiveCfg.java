/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration.backup;

import io.camunda.zeebe.broker.system.configuration.ConfigurationEntry;
import java.time.Duration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.util.unit.DataSize;

/**
 * Settings of the log archive, {@code zeebe.broker.data.backup.logArchive.*}. The store type and
 * its connection settings are taken from the enclosing {@link BackupCfg}.
 *
 * <p>{@link #chunkSize} and {@link #rootEvery} influence replicated state and must be identical on
 * all brokers.
 */
@NullMarked
public class LogArchiveCfg implements ConfigurationEntry {

  private boolean enabled = false;
  private String timeline = "tl-0";
  private DataSize cutMaxBytes = DataSize.ofMegabytes(16);
  private Duration cutInterval = Duration.ofSeconds(2);
  private Duration idleCutInterval = Duration.ofMinutes(5);
  private Duration snapshotUploadInterval = Duration.ofMinutes(30);
  private int chunkSize = 1000;
  private int rootEvery = 100;
  private LogArchiveEventPlaneCfg eventPlane = new LogArchiveEventPlaneCfg();
  private @Nullable String testOnlyAdvertisedVersion;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  /** The timeline id used when the partition directory has no timeline file. */
  public String getTimeline() {
    return timeline;
  }

  public void setTimeline(final String timeline) {
    this.timeline = timeline;
  }

  public DataSize getCutMaxBytes() {
    return cutMaxBytes;
  }

  public void setCutMaxBytes(final DataSize cutMaxBytes) {
    this.cutMaxBytes = cutMaxBytes;
  }

  public Duration getCutInterval() {
    return cutInterval;
  }

  public void setCutInterval(final Duration cutInterval) {
    this.cutInterval = cutInterval;
  }

  /**
   * How often an object is cut while every committed, unarchived entry holds only the log archive's
   * own records. Archiving produces such entries itself, so cutting them on {@link #cutInterval}
   * would keep an idle partition uploading forever.
   */
  public Duration getIdleCutInterval() {
    return idleCutInterval;
  }

  public void setIdleCutInterval(final Duration idleCutInterval) {
    this.idleCutInterval = idleCutInterval;
  }

  public Duration getSnapshotUploadInterval() {
    return snapshotUploadInterval;
  }

  public void setSnapshotUploadInterval(final Duration snapshotUploadInterval) {
    this.snapshotUploadInterval = snapshotUploadInterval;
  }

  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(final int chunkSize) {
    this.chunkSize = chunkSize;
  }

  public int getRootEvery() {
    return rootEvery;
  }

  public void setRootEvery(final int rootEvery) {
    this.rootEvery = rootEvery;
  }

  public LogArchiveEventPlaneCfg getEventPlane() {
    return eventPlane;
  }

  public void setEventPlane(final LogArchiveEventPlaneCfg eventPlane) {
    this.eventPlane = eventPlane;
  }

  /**
   * TEST ONLY. If set, the broker gossips this version in its broker info instead of its own, so
   * that an integration test can make a broker look like it was not upgraded yet and keep the log
   * archive's upgrade gate closed (spec section 18.1). Never set this in a real deployment.
   */
  public @Nullable String getTestOnlyAdvertisedVersion() {
    return testOnlyAdvertisedVersion;
  }

  public void setTestOnlyAdvertisedVersion(final @Nullable String testOnlyAdvertisedVersion) {
    this.testOnlyAdvertisedVersion = testOnlyAdvertisedVersion;
  }

  @Override
  public String toString() {
    return "LogArchiveCfg{"
        + "enabled="
        + enabled
        + ", timeline='"
        + timeline
        + '\''
        + ", cutMaxBytes="
        + cutMaxBytes
        + ", cutInterval="
        + cutInterval
        + ", idleCutInterval="
        + idleCutInterval
        + ", snapshotUploadInterval="
        + snapshotUploadInterval
        + ", chunkSize="
        + chunkSize
        + ", rootEvery="
        + rootEvery
        + ", eventPlane="
        + eventPlane
        + ", testOnlyAdvertisedVersion="
        + testOnlyAdvertisedVersion
        + '}';
  }
}

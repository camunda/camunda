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
  private String compactionMode = "CLUSTER";
  private @Nullable Duration retentionWindow;
  private Duration releaseInterval = Duration.ofMinutes(1);
  private int mergeMaxObjects = 1000;
  private DataSize mergeFrameBytes = DataSize.ofMegabytes(32);
  private Duration cleanupInterval = Duration.ofMinutes(1);
  private Duration cleanupGracePeriod = Duration.ofMinutes(5);
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

  /**
   * Who compacts and deletes: {@code CLUSTER} (default, spec sections 20 to 22) or {@code
   * AUTHORITY}, where only the event plane does and the cluster declares a restore hold (spec
   * section 25). The mode is fixed per timeline: a change applies to the next timeline the archiver
   * starts.
   */
  public String getCompactionMode() {
    return compactionMode;
  }

  public void setCompactionMode(final String compactionMode) {
    if (!"CLUSTER".equals(compactionMode) && !"AUTHORITY".equals(compactionMode)) {
      throw new IllegalArgumentException(
          "Expected logArchive.compactionMode to be CLUSTER or AUTHORITY, but got '%s'"
              .formatted(compactionMode));
    }
    this.compactionMode = compactionMode;
  }

  /**
   * Restore points younger than this stay restorable; older log is released to the event plane
   * (spec section 21). Null, the default, never releases anything.
   */
  public @Nullable Duration getRetentionWindow() {
    return retentionWindow;
  }

  public void setRetentionWindow(final @Nullable Duration retentionWindow) {
    this.retentionWindow = retentionWindow;
  }

  /** How often the releaser checks the restore floor. */
  public Duration getReleaseInterval() {
    return releaseInterval;
  }

  public void setReleaseInterval(final Duration releaseInterval) {
    this.releaseInterval = releaseInterval;
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

  /** Merge at most this many objects at once, and merge as soon as this many are unmerged. */
  public int getMergeMaxObjects() {
    return mergeMaxObjects;
  }

  public void setMergeMaxObjects(final int mergeMaxObjects) {
    this.mergeMaxObjects = mergeMaxObjects;
  }

  /** Entries per zstd frame of a merged object. */
  public DataSize getMergeFrameBytes() {
    return mergeFrameBytes;
  }

  public void setMergeFrameBytes(final DataSize mergeFrameBytes) {
    this.mergeFrameBytes = mergeFrameBytes;
  }

  /** How often the cleaner lists the bucket for replaced and orphaned objects. */
  public Duration getCleanupInterval() {
    return cleanupInterval;
  }

  public void setCleanupInterval(final Duration cleanupInterval) {
    this.cleanupInterval = cleanupInterval;
  }

  /** How long a replaced or orphaned object stays after it became garbage. */
  public Duration getCleanupGracePeriod() {
    return cleanupGracePeriod;
  }

  public void setCleanupGracePeriod(final Duration cleanupGracePeriod) {
    this.cleanupGracePeriod = cleanupGracePeriod;
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
        + ", mergeMaxObjects="
        + mergeMaxObjects
        + ", mergeFrameBytes="
        + mergeFrameBytes
        + ", cleanupInterval="
        + cleanupInterval
        + ", cleanupGracePeriod="
        + cleanupGracePeriod
        + ", eventPlane="
        + eventPlane
        + ", testOnlyAdvertisedVersion="
        + testOnlyAdvertisedVersion
        + '}';
  }
}

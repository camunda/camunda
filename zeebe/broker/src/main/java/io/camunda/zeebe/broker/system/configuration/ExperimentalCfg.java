/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.system.configuration.RaftCfg.FlushConfig;
import io.camunda.zeebe.broker.system.configuration.engine.EngineCfg;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

/**
 * Be aware that all configuration which are part of this class are experimental, which means they
 * are subject to change and to drop. It might be that also some of them are actually dangerous so
 * be aware when you change one of these!
 */
public class ExperimentalCfg implements ConfigurationEntry {

  public static final int DEFAULT_MAX_APPENDS_PER_FOLLOWER = 6;
  public static final DataSize DEFAULT_MAX_APPEND_BATCH_SIZE = DataSize.ofKilobytes(32);
  public static final boolean DEFAULT_DISABLE_EXPLICIT_RAFT_FLUSH = false;
  public static final boolean DEFAULT_VERSION_CHECK_ENABLED = true;
  private static final boolean DEFAULT_SEND_ON_LEGACY_SUBJECT = true;
  private static final boolean DEFAULT_RECEIVE_ON_LEGACY_SUBJECT = true;
  private static final String DEFAULT_ENGINE_NAME = "default";

  private boolean continuousBackups = false;

  /**
   * Allows to enable/disable the version check, that prevents us on migrating to alpha versions,
   * etc.
   */
  private boolean versionCheckRestrictionEnabled = DEFAULT_VERSION_CHECK_ENABLED;

  private int maxAppendsPerFollower = DEFAULT_MAX_APPENDS_PER_FOLLOWER;
  private DataSize maxAppendBatchSize = DEFAULT_MAX_APPEND_BATCH_SIZE;
  private boolean disableExplicitRaftFlush = DEFAULT_DISABLE_EXPLICIT_RAFT_FLUSH;
  private boolean sendOnLegacySubject = DEFAULT_SEND_ON_LEGACY_SUBJECT;
  private boolean receiveOnLegacySubject = DEFAULT_RECEIVE_ON_LEGACY_SUBJECT;
  private String defaultEngineName = DEFAULT_ENGINE_NAME;
  private RocksdbCfg rocksdb = new RocksdbCfg();
  private ExperimentalRaftCfg raft = new ExperimentalRaftCfg();
  private PartitioningCfg partitioning = new PartitioningCfg();
  private QueryApiCfg queryApi = new QueryApiCfg();
  private ConsistencyCheckCfg consistencyChecks = new ConsistencyCheckCfg();
  private EngineCfg engine = new EngineCfg();
  private FeatureFlagsCfg features = new FeatureFlagsCfg();

  public boolean isContinuousBackups() {
    return continuousBackups;
  }

  public void setContinuousBackups(final boolean continuousBackups) {
    this.continuousBackups = continuousBackups;
  }

  public boolean isVersionCheckRestrictionEnabled() {
    return versionCheckRestrictionEnabled;
  }

  public void setVersionCheckRestrictionEnabled(final boolean versionCheckRestrictionEnabled) {
    this.versionCheckRestrictionEnabled = versionCheckRestrictionEnabled;
  }

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    rocksdb.init(globalConfig, brokerBase);
    raft.init(globalConfig, brokerBase);
    engine.init(globalConfig, brokerBase);
  }

  public int getMaxAppendsPerFollower() {
    return maxAppendsPerFollower;
  }

  public void setMaxAppendsPerFollower(final int maxAppendsPerFollower) {
    this.maxAppendsPerFollower = maxAppendsPerFollower;
  }

  public DataSize getMaxAppendBatchSize() {
    return maxAppendBatchSize;
  }

  public void setMaxAppendBatchSize(final DataSize maxAppendBatchSize) {
    this.maxAppendBatchSize = maxAppendBatchSize;
  }

  public long getMaxAppendBatchSizeInBytes() {
    return Optional.ofNullable(maxAppendBatchSize).orElse(DEFAULT_MAX_APPEND_BATCH_SIZE).toBytes();
  }

  /**
   * @deprecated Deprecated in favor of {@link RaftCfg#getFlush()}. The equivalent is a null
   *     configuration, e.g. {@link new FlushConfig(null)}. Will be removed in 8.3.0.
   */
  @Deprecated(since = "8.2.0", forRemoval = true)
  public boolean isDisableExplicitRaftFlush() {
    return disableExplicitRaftFlush;
  }

  /**
   * @deprecated Deprecated in favor of {@link RaftCfg#setFlush(FlushConfig)}. To disable, you can
   *     call {@link RaftCfg#setFlush(FlushConfig)} with a null configuration, e.g. {@code new
   *     FlushConfig(null)}. Will be removed in 8.3.0.
   */
  @Deprecated(since = "8.2.0", forRemoval = true)
  public void setDisableExplicitRaftFlush(final boolean disableExplicitRaftFlush) {
    this.disableExplicitRaftFlush = disableExplicitRaftFlush;
  }

  public RocksdbCfg getRocksdb() {
    return rocksdb;
  }

  public void setRocksdb(final RocksdbCfg rocksdb) {
    this.rocksdb = rocksdb;
  }

  public ExperimentalRaftCfg getRaft() {
    return raft;
  }

  public void setRaft(final ExperimentalRaftCfg raft) {
    this.raft = raft;
  }

  public PartitioningCfg getPartitioning() {
    return partitioning;
  }

  public void setPartitioning(final PartitioningCfg partitioning) {
    this.partitioning = partitioning;
  }

  public QueryApiCfg getQueryApi() {
    return queryApi;
  }

  public void setQueryApi(final QueryApiCfg queryApi) {
    this.queryApi = queryApi;
  }

  public ConsistencyCheckCfg getConsistencyChecks() {
    return consistencyChecks;
  }

  public void setConsistencyChecks(final ConsistencyCheckCfg consistencyChecks) {
    this.consistencyChecks = consistencyChecks;
  }

  public EngineCfg getEngine() {
    return engine;
  }

  public void setEngine(final EngineCfg engine) {
    this.engine = engine;
  }

  public FeatureFlagsCfg getFeatures() {
    return features;
  }

  public void setFeatures(final FeatureFlagsCfg features) {
    this.features = features;
  }

  public boolean isSendOnLegacySubject() {
    return sendOnLegacySubject;
  }

  public void setSendOnLegacySubject(final boolean sendOnLegacySubject) {
    this.sendOnLegacySubject = sendOnLegacySubject;
  }

  public boolean isReceiveOnLegacySubject() {
    return receiveOnLegacySubject;
  }

  public void setReceiveOnLegacySubject(final boolean receiveOnLegacySubject) {
    this.receiveOnLegacySubject = receiveOnLegacySubject;
  }

  public String getDefaultEngineName() {
    return defaultEngineName;
  }

  public void setDefaultEngineName(final String defaultEngineName) {
    this.defaultEngineName = defaultEngineName;
  }

  @Override
  public String toString() {
    return "ExperimentalCfg{"
        + "maxAppendsPerFollower="
        + maxAppendsPerFollower
        + ", maxAppendBatchSize="
        + maxAppendBatchSize
        + ", disableExplicitRaftFlush="
        + disableExplicitRaftFlush
        + ", rocksdb="
        + rocksdb
        + ", partitioning="
        + partitioning
        + ", queryApi="
        + queryApi
        + ", consistencyChecks="
        + consistencyChecks
        + ", engineCfg="
        + engine
        + ", features="
        + features
        + ", sendOnLegacySubject="
        + sendOnLegacySubject
        + ", receiveOnLegacySubject="
        + receiveOnLegacySubject
        + ", defaultEngineName="
        + defaultEngineName
        + '}';
  }
}

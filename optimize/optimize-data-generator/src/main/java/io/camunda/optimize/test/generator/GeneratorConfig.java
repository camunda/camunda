/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX;

/**
 * Immutable configuration for {@link ZeebeProcessDataGenerator}.
 *
 * <p>Owns the process-definition pool, key-derivation logic, and all tunable knobs. Configuration
 * concerns are kept here so the generator class itself only orchestrates.
 *
 * <pre>{@code
 * GeneratorConfig config = new GeneratorConfig.Builder()
 *     .instanceCount(50_000)
 *     .processDefinitionCount(5)
 *     .zeebeRecordPrefix(zeebeExtension.getZeebeRecordPrefix())
 *     .build();
 * }</pre>
 */
public final class GeneratorConfig {

  private static final String SIMPLE_PROCESS_ID = "order-fulfillment";
  private static final String MEDIUM_PROCESS_ID = "invoice-processing";
  private static final String COMPLEX_PROCESS_ID = "loan-approval";
  private static final String BRANCHING_PROCESS_ID = "claim-processing";
  private static final String EVENT_BASED_PROCESS_ID = "customer-onboarding";
  private static final String FRAUD_PROCESS_ID = "fraud-dispute-handling";

  static final String[] DEFAULT_PROCESS_IDS = {
    SIMPLE_PROCESS_ID,
    MEDIUM_PROCESS_ID,
    COMPLEX_PROCESS_ID,
    BRANCHING_PROCESS_ID,
    EVENT_BASED_PROCESS_ID,
    FRAUD_PROCESS_ID,
  };

  /** Total number of process instances to generate. */
  public final int instanceCount;

  /**
   * Number of distinct process definitions to use (ignored if {@code processDefinitionKeys} is
   * set).
   */
  public final int processDefinitionCount;

  /** Explicit list of process definition IDs (overrides {@code processDefinitionCount}). */
  public final String[] processDefinitionKeys;

  /**
   * Number of calendar months of history to simulate. Instances are spread uniformly across this
   * window ending at "now".
   */
  public final int monthsOfHistory;

  /**
   * Prefix for all Zeebe record indexes (e.g. {@code zeebe-record-abc123}). Each index will be
   * {@code {prefix}-{type}}, e.g. {@code zeebe-record-abc123-process-instance}.
   */
  public final String zeebeRecordPrefix;

  /** Partition ID written to all generated records. */
  public final int partitionId;

  /**
   * Base value for process instance keys. Use a value that doesn't overlap with other generators.
   */
  public final long instanceKeyOffset;

  /**
   * Starting {@code position} value for all record types. Must be higher than the last imported
   * position if you want the import pipeline to pick up these records.
   */
  public final long positionOffset;

  /** Number of records per ES bulk request. */
  public final int batchSize;

  /** RNG seed for reproducible data sets. */
  public final long seed;

  /**
   * Fraction of generated instances that also receive a {@code UPDATED} variable record after all
   * {@code CREATED} records have been written. Range [0.0, 1.0]. Default is {@code 0.0} (disabled).
   */
  public final double updateRate;

  /**
   * Fraction of generated instances that also receive an agent instance lifecycle (CREATED →
   * UPDATED… → COMPLETED). Range [0.0, 1.0]. Default is {@code 0.3}.
   */
  public final double agentInstanceRate;

  /**
   * Number of versions to generate per process definition. Each process will have versions 1
   * through {@code processVersionCount} (inclusive). Default is {@code 5}.
   */
  public final int processVersionCount;

  /**
   * Prefix for Optimize-processed indexes (e.g. {@code optimize}). Used to directly seed the
   * Optimize {@code process-definition} index so the process ComboBox is populated without relying
   * on the Optimize importer pipeline. Default is {@code "optimize"}.
   */
  public final String optimizeIndexPrefix;

  private GeneratorConfig(final Builder builder) {
    this.instanceCount = builder.instanceCount;
    this.processDefinitionCount = builder.processDefinitionCount;
    this.processDefinitionKeys = builder.processDefinitionKeys;
    this.monthsOfHistory = builder.monthsOfHistory;
    this.zeebeRecordPrefix = builder.zeebeRecordPrefix;
    this.partitionId = builder.partitionId;
    this.instanceKeyOffset = builder.instanceKeyOffset;
    this.positionOffset = builder.positionOffset;
    this.batchSize = builder.batchSize;
    this.seed = builder.seed;
    this.updateRate = builder.updateRate;
    this.agentInstanceRate = builder.agentInstanceRate;
    this.processVersionCount = builder.processVersionCount;
    this.optimizeIndexPrefix = builder.optimizeIndexPrefix;
  }

  /**
   * Returns the resolved list of process definition IDs. Explicit keys take priority; otherwise a
   * slice of the built-in defaults is used.
   */
  String[] resolveProcessIds() {
    if (processDefinitionKeys != null && processDefinitionKeys.length > 0) {
      return processDefinitionKeys;
    }
    final int count = Math.min(processDefinitionCount, DEFAULT_PROCESS_IDS.length);
    final String[] resolved = new String[count];
    System.arraycopy(DEFAULT_PROCESS_IDS, 0, resolved, 0, count);
    return resolved;
  }

  /**
   * Derives a stable numeric process definition key from the definition's array index and version.
   * Stable across generator calls with the same config so variable records and PI records reference
   * the same key.
   */
  long definitionKeyFor(final int defIndex, final int version) {
    return ((long) defIndex * 10 + version) * 1_000L + partitionId;
  }

  public static final class Builder {

    public static final long DEFAULT_INSTANCE_KEY_OFFSET = 3_000_000_000L;

    private int instanceCount = 10_000;
    private int processDefinitionCount = 6;
    private String[] processDefinitionKeys = null;
    private int monthsOfHistory = 6;
    private String zeebeRecordPrefix = ZEEBE_RECORD_TEST_PREFIX + "-test";
    private int partitionId = 1;
    private long instanceKeyOffset = DEFAULT_INSTANCE_KEY_OFFSET;
    private long positionOffset = 0L;
    private int batchSize = 1_000;
    private long seed = 42L;
    private double updateRate = 0.0;
    private double agentInstanceRate = 0.3;
    private int processVersionCount = 5;
    private String optimizeIndexPrefix = "optimize";

    public Builder instanceCount(final int instanceCount) {
      this.instanceCount = instanceCount;
      return this;
    }

    public Builder processDefinitionCount(final int processDefinitionCount) {
      this.processDefinitionCount = processDefinitionCount;
      return this;
    }

    public Builder processDefinitionKeys(final String... keys) {
      this.processDefinitionKeys = keys;
      return this;
    }

    public Builder monthsOfHistory(final int months) {
      this.monthsOfHistory = months;
      return this;
    }

    public Builder zeebeRecordPrefix(final String prefix) {
      this.zeebeRecordPrefix = prefix;
      return this;
    }

    public Builder partitionId(final int partitionId) {
      this.partitionId = partitionId;
      return this;
    }

    public Builder instanceKeyOffset(final long offset) {
      this.instanceKeyOffset = offset;
      return this;
    }

    public Builder positionOffset(final long offset) {
      this.positionOffset = offset;
      return this;
    }

    public Builder batchSize(final int batchSize) {
      this.batchSize = batchSize;
      return this;
    }

    public Builder seed(final long seed) {
      this.seed = seed;
      return this;
    }

    public Builder updateRate(final double updateRate) {
      this.updateRate = updateRate;
      return this;
    }

    public Builder agentInstanceRate(final double agentInstanceRate) {
      this.agentInstanceRate = agentInstanceRate;
      return this;
    }

    public Builder processVersionCount(final int count) {
      this.processVersionCount = count;
      return this;
    }

    public Builder optimizeIndexPrefix(final String prefix) {
      this.optimizeIndexPrefix = prefix;
      return this;
    }

    public GeneratorConfig build() {
      return new GeneratorConfig(this);
    }
  }
}

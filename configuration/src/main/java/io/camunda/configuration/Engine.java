/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MAX_PROCESS_DEPTH;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Engine {
  private static final String PREFIX = "camunda.processing.engine";

  private static final Set<String> LEGACY_MAX_PROCESS_DEPTH_PROPERTIES =
      Set.of("zeebe.broker.experimental.engine.maxProcessDepth");

  private static final boolean DEFAULT_EVALUATE_DUPLICATE_OUTPUT_MAPPING_TARGETS_IN_ORDER = true;
  private static final boolean DEFAULT_EVALUATE_INPUT_MAPPINGS_ONE_BY_ONE = true;

  /** Configuration properties for the engine's distribution settings. */
  @NestedConfigurationProperty private Distribution distribution = new Distribution();

  /** Configuration properties for the engine's batch operation settings. */
  @NestedConfigurationProperty
  private EngineBatchOperation batchOperations = new EngineBatchOperation();

  @NestedConfigurationProperty private EngineJob job = new EngineJob();

  /** Configuration properties for the engine's caches. */
  @NestedConfigurationProperty private EngineCaches caches = new EngineCaches();

  /** Configuration properties for the engine's message TTL checker. */
  @NestedConfigurationProperty private EngineMessages messages = new EngineMessages();

  /** Configuration properties for the engine's BPMN/DMN validators. */
  @NestedConfigurationProperty private EngineValidators validators = new EngineValidators();

  /** Configuration properties for the engine's secret resolution scheduler. */
  @NestedConfigurationProperty private EngineSecrets secrets = new EngineSecrets();

  /**
   * Configures the maximum depth of nested call activities allowed before an incident is raised, to
   * guard against unbounded process recursion.
   */
  private int maxProcessDepth = DEFAULT_MAX_PROCESS_DEPTH;

  /**
   * Controls how output-variable mappings with colliding targets (the same target, or one target a
   * prefix of another, e.g. {@code a} and {@code a.b}) are evaluated when a process has more than
   * one mapping writing to such a target. When enabled (default), every mapping's source is
   * evaluated, in modeling order, and a failing source raises an incident. When disabled, the
   * earlier colliding mapping's source is silently skipped, never evaluated, reproducing the
   * behavior prior to this change. Disabling this can suppress an incident from a source that would
   * otherwise now fail on an already-deployed process, but is a compatibility escape hatch, not
   * something to reach for by default.
   */
  private boolean evaluateDuplicateOutputMappingTargetsInOrder =
      DEFAULT_EVALUATE_DUPLICATE_OUTPUT_MAPPING_TARGETS_IN_ORDER;

  /**
   * Controls how the input-variable mappings of an element are evaluated. When enabled (default),
   * each mapping's source is evaluated on its own, in modeling order, so a later mapping can
   * reference what an earlier one produced. When disabled, all mappings of the element are
   * evaluated as the single combined FEEL context expression they compiled into before this change,
   * reproducing the behavior of versions up to 8.7.35 and 8.8.33.
   *
   * <p>Disabling this reinstates the bugs that change fixed - a mapping declared between two
   * mappings that share a target root is invisible to the later one, and per-field copies of one
   * variable drop all but one field - so it is a compatibility escape hatch, not something to reach
   * for by default. A process whose combined expression does not parse (which deploys fine either
   * way) keeps being evaluated one mapping at a time even when this is disabled, rather than
   * failing every activation.
   */
  private boolean evaluateInputMappingsOneByOne = DEFAULT_EVALUATE_INPUT_MAPPINGS_ONE_BY_ONE;

  public Distribution getDistribution() {
    return distribution;
  }

  public void setDistribution(final Distribution distribution) {
    this.distribution = distribution;
  }

  public EngineBatchOperation getBatchOperations() {
    return batchOperations;
  }

  public void setBatchOperations(final EngineBatchOperation batchOperations) {
    this.batchOperations = batchOperations;
  }

  public EngineJob getJob() {
    return job;
  }

  public void setJob(final EngineJob job) {
    this.job = job;
  }

  public EngineCaches getCaches() {
    return caches;
  }

  public void setCaches(final EngineCaches caches) {
    this.caches = caches;
  }

  public EngineMessages getMessages() {
    return messages;
  }

  public void setMessages(final EngineMessages messages) {
    this.messages = messages;
  }

  public EngineValidators getValidators() {
    return validators;
  }

  public void setValidators(final EngineValidators validators) {
    this.validators = validators;
  }

  public EngineSecrets getSecrets() {
    return secrets;
  }

  public void setSecrets(final EngineSecrets secrets) {
    this.secrets = secrets;
  }

  public int getMaxProcessDepth() {
    return UnifiedConfigurationHelper.validateLegacyConfigurationUnsafe(
        PREFIX + ".max-process-depth",
        maxProcessDepth,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_PROCESS_DEPTH_PROPERTIES);
  }

  public void setMaxProcessDepth(final int maxProcessDepth) {
    this.maxProcessDepth = maxProcessDepth;
  }

  public boolean isEvaluateDuplicateOutputMappingTargetsInOrder() {
    return evaluateDuplicateOutputMappingTargetsInOrder;
  }

  public boolean isEvaluateInputMappingsOneByOne() {
    return evaluateInputMappingsOneByOne;
  }

  public void setEvaluateInputMappingsOneByOne(final boolean evaluateInputMappingsOneByOne) {
    this.evaluateInputMappingsOneByOne = evaluateInputMappingsOneByOne;
  }

  public void setEvaluateDuplicateOutputMappingTargetsInOrder(
      final boolean evaluateDuplicateOutputMappingTargetsInOrder) {
    this.evaluateDuplicateOutputMappingTargetsInOrder =
        evaluateDuplicateOutputMappingTargetsInOrder;
  }
}

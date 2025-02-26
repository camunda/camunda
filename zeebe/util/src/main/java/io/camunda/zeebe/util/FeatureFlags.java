/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public final class FeatureFlags {

  /* To add a new feature toggle, please follow these steps:
   *
   * - add a record property to this class, and extend the test for this class
   *
   * - define the default value. When introducing a new feature flag the default
   *   value should be 'false'. This way the feature is disabled by default
   *   for all customers who do not change their configuration.
   *   As we gain more confidence in the efficacy of the feature flag, the
   *   default value can be set to 'true'
   *
   * - define a default value to be used in tests
   *
   * - make sure that all relevant tests use the default value for tests
   *
   * - add a field, getter and setter to FeatureFlagsCfg
   *
   * - add a description of the feature flag to
   *    - dist/src/main/config/broker.standalone.yaml.template
   *    - dist/src/main/config/broker.yaml.template
   *
   * - add test cases to FeaturesFlagCfgTest and feature-flags-cfg.yaml
   *
   * Be careful with parameter order in constructor calls!
   */

  //  protected static final boolean FOO_DEFAULT = false;

  private static final boolean YIELDING_DUE_DATE_CHECKER = true;
  private static final boolean ENABLE_ACTOR_METRICS = false;

  private static final boolean ENABLE_MSG_TTL_CHECKER_ASYNC = false;
  private static final boolean ENABLE_DUE_DATE_CHECKER_ASYNC = false;
  private static final boolean ENABLE_STRAIGHT_THOUGH_PROCESSING_LOOP_DETECTOR = true;
  private static final boolean ENABLE_PARTITION_SCALING = false;
  private static final boolean ENABLE_IDENTITY_SETUP = true;

  private boolean yieldingDueDateChecker;
  private boolean enableActorMetrics;
  private boolean enableMessageTTLCheckerAsync;
  private boolean enableTimerDueDateCheckerAsync;
  private boolean enableStraightThroughProcessingLoopDetector;
  private boolean enablePartitionScaling;
  private boolean enableIdentitySetup;

  public FeatureFlags(
      final boolean yieldingDueDateChecker,
      final boolean enableActorMetrics,
      final boolean enableMessageTTLCheckerAsync,
      final boolean enableTimerDueDateCheckerAsync,
      final boolean enableStraightThroughProcessingLoopDetector,
      final boolean enablePartitionScaling,
      final boolean enableIdentitySetup
      /*, boolean foo*/ ) {
    this.yieldingDueDateChecker = yieldingDueDateChecker;
    this.enableActorMetrics = enableActorMetrics;
    this.enableMessageTTLCheckerAsync = enableMessageTTLCheckerAsync;
    this.enableTimerDueDateCheckerAsync = enableTimerDueDateCheckerAsync;
    this.enableStraightThroughProcessingLoopDetector = enableStraightThroughProcessingLoopDetector;
    this.enablePartitionScaling = enablePartitionScaling;
    this.enableIdentitySetup = enableIdentitySetup;
  }

  public static FeatureFlags createDefault() {
    return new FeatureFlags(
        YIELDING_DUE_DATE_CHECKER,
        ENABLE_ACTOR_METRICS,
        ENABLE_MSG_TTL_CHECKER_ASYNC,
        ENABLE_DUE_DATE_CHECKER_ASYNC,
        ENABLE_STRAIGHT_THOUGH_PROCESSING_LOOP_DETECTOR,
        ENABLE_PARTITION_SCALING,
        ENABLE_IDENTITY_SETUP
        /*, FOO_DEFAULT*/ );
  }

  /**
   * Only to be used in tests
   *
   * @return
   */
  public static FeatureFlags createDefaultForTests() {
    return new FeatureFlags(
        true, /* YIELDING_DUE_DATE_CHECKER*/
        false, /* ENABLE_ACTOR_METRICS */
        true, /* ENABLE_MSG_TTL_CHECKER_ASYNC */
        true, /* ENABLE_DUE_DATE_CHECKER_ASYNC */
        true, /* ENABLE_STRAIGHT_THOUGH_PROCESSING_LOOP_DETECTOR */
        true, /* ENABLE_PARTITION_SCALING */
        false /* ENABLE_IDENTITY_SETUP */
        /*, FOO_DEFAULT*/ );
  }

  public boolean yieldingDueDateChecker() {
    return yieldingDueDateChecker;
  }

  public boolean enableActorMetrics() {
    return enableActorMetrics;
  }

  public boolean enableMessageTTLCheckerAsync() {
    return enableMessageTTLCheckerAsync;
  }

  public boolean enableTimerDueDateCheckerAsync() {
    return enableTimerDueDateCheckerAsync;
  }

  public boolean enableStraightThroughProcessingLoopDetector() {
    return enableStraightThroughProcessingLoopDetector;
  }

  public boolean enablePartitionScaling() {
    return enablePartitionScaling;
  }

  public boolean enableIdentitySetup() {
    return enableIdentitySetup;
  }

  public void setYieldingDueDateChecker(final boolean yieldingDueDateChecker) {
    this.yieldingDueDateChecker = yieldingDueDateChecker;
  }

  public void setEnableActorMetrics(final boolean enableActorMetrics) {
    this.enableActorMetrics = enableActorMetrics;
  }

  public void setEnableMessageTTLCheckerAsync(final boolean enableMessageTTLCheckerAsync) {
    this.enableMessageTTLCheckerAsync = enableMessageTTLCheckerAsync;
  }

  public void setEnableTimerDueDateCheckerAsync(final boolean enableTimerDueDateCheckerAsync) {
    this.enableTimerDueDateCheckerAsync = enableTimerDueDateCheckerAsync;
  }

  public void setEnableStraightThroughProcessingLoopDetector(
      final boolean enableStraightThroughProcessingLoopDetector) {
    this.enableStraightThroughProcessingLoopDetector = enableStraightThroughProcessingLoopDetector;
  }

  public void setEnablePartitionScaling(final boolean enablePartitionScaling) {
    this.enablePartitionScaling = enablePartitionScaling;
  }

  public void setEnableIdentitySetup(final boolean enableIdentitySetup) {
    this.enableIdentitySetup = enableIdentitySetup;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}

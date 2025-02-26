/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.camunda.zeebe.util.FeatureFlags;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

@JsonSerialize // this annotation seems to implicitly disable
// SerializationFeature.FAIL_ON_EMPTY_BEANS
public final class FeatureFlagsCfg {

  // To add a new feature flag, read the comments in FeatureFlags

  private static final FeatureFlags DEFAULT_SETTINGS = FeatureFlags.createDefault();
  //
  //  private boolean enableFoo = DEFAULT_SETTINGS.foo();
  //
  //  public boolean isEnableFoo() {
  //    return enableFoo;
  //  }
  //
  //  public void setEnableFoo(final boolean enableFoo) {
  //    this.enableFoo = enableFoo;
  //  }

  private boolean enableYieldingDueDateChecker = DEFAULT_SETTINGS.yieldingDueDateChecker();
  private boolean enableActorMetrics = DEFAULT_SETTINGS.enableActorMetrics();
  private boolean enableMessageTtlCheckerAsync = DEFAULT_SETTINGS.enableMessageTTLCheckerAsync();
  private boolean enableTimerDueDateCheckerAsync =
      DEFAULT_SETTINGS.enableTimerDueDateCheckerAsync();
  private boolean enableStraightThroughProcessingLoopDetector =
      DEFAULT_SETTINGS.enableStraightThroughProcessingLoopDetector();
  private boolean enablePartitionScaling = DEFAULT_SETTINGS.enablePartitionScaling();
  private boolean enableIdentitySetup = DEFAULT_SETTINGS.enableIdentitySetup();

  public boolean isEnableYieldingDueDateChecker() {
    return enableYieldingDueDateChecker;
  }

  public void setEnableYieldingDueDateChecker(final boolean enableYieldingDueDateChecker) {
    this.enableYieldingDueDateChecker = enableYieldingDueDateChecker;
  }

  public boolean isEnableActorMetrics() {
    return enableActorMetrics;
  }

  public void setEnableActorMetrics(final boolean enableActorMetrics) {
    this.enableActorMetrics = enableActorMetrics;
  }

  public boolean isEnableMessageTtlCheckerAsync() {
    return enableMessageTtlCheckerAsync;
  }

  public void setEnableMessageTtlCheckerAsync(final boolean enableMessageTtlCheckerAsync) {
    this.enableMessageTtlCheckerAsync = enableMessageTtlCheckerAsync;
  }

  public boolean isEnableTimerDueDateCheckerAsync() {
    return enableTimerDueDateCheckerAsync;
  }

  public void setEnableTimerDueDateCheckerAsync(final boolean enableTimerDueDateCheckerAsync) {
    this.enableTimerDueDateCheckerAsync = enableTimerDueDateCheckerAsync;
  }

  public boolean isEnableStraightThroughProcessingLoopDetector() {
    return enableStraightThroughProcessingLoopDetector;
  }

  public void setEnableStraightThroughProcessingLoopDetector(
      final boolean enableStraightThroughProcessingLoopDetector) {
    this.enableStraightThroughProcessingLoopDetector = enableStraightThroughProcessingLoopDetector;
  }

  public boolean isEnablePartitionScaling() {
    return enablePartitionScaling;
  }

  public void setEnablePartitionScaling(final boolean enablePartitionScaling) {
    this.enablePartitionScaling = enablePartitionScaling;
  }

  public boolean isEnableIdentitySetup() {
    return enableIdentitySetup;
  }

  public void setEnableIdentitySetup(final boolean enableIdentitySetup) {
    this.enableIdentitySetup = enableIdentitySetup;
  }

  public FeatureFlags toFeatureFlags() {
    return new FeatureFlags(
        enableYieldingDueDateChecker,
        enableActorMetrics,
        enableMessageTtlCheckerAsync,
        enableTimerDueDateCheckerAsync,
        enableStraightThroughProcessingLoopDetector,
        enablePartitionScaling,
        enableIdentitySetup
        /*, enableFoo*/ );
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}

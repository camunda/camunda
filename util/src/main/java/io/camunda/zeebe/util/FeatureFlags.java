/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public record FeatureFlags(
    boolean yieldingDueDateChecker, boolean enableActorMetrics /*, boolean foo*/) {

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

  private static final boolean YIELDING_DUE_DATE_CHECKER = false;
  private static final boolean ENABLE_ACTOR_METRICS = false;

  public static FeatureFlags createDefault() {
    return new FeatureFlags(YIELDING_DUE_DATE_CHECKER, ENABLE_ACTOR_METRICS /*, FOO_DEFAULT*/);
  }

  /**
   * Only to be used in tests
   *
   * @return
   */
  public static FeatureFlags createDefaultForTests() {
    return new FeatureFlags(
        true, /* YIELDING_DUE_DATE_CHECKER*/ false /* ENABLE_ACTOR_METRICS */
        /*, FOO_DEFAULT*/ );
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }
}

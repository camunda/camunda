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

  // private final boolean foo;

  private static final boolean YIELDING_DUE_DATE_CHECKER_DEFAULT = false;

  private final boolean yieldingDueDateChecker;

  public FeatureFlags(final boolean yieldingDueDateChecker) {
    this.yieldingDueDateChecker = yieldingDueDateChecker;
  }

  public boolean yieldingDueDateChecker() {
    return yieldingDueDateChecker;
  }

  //  public boolean foo() {
  //    return foo;
  //  }

  @Override
  public int hashCode() {
    return 1;
  }

  @Override
  public boolean equals(final Object obj) {
    return obj == this || obj != null && obj.getClass() == getClass();
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE);
  }

  //  protected static final boolean FOO_DEFAULT = false;
  //
  public static FeatureFlags createDefault() {
    return new FeatureFlags(/*FOO_DEFAULT*/ YIELDING_DUE_DATE_CHECKER_DEFAULT);
  }

  public static FeatureFlags createDefaultForTests() {
    return new FeatureFlags(/* YIELDING_DUE_DATE_CHECKER*/ true /*, FOO_DEFAULT*/);
  }
}

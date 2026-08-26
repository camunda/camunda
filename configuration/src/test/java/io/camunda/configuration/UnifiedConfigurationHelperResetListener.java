/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Resets {@link UnifiedConfigurationHelper}'s static {@code environment} reference after every
 * Spring test class (including {@code @Nested} classes, which each get their own {@link
 * TestContext}).
 *
 * <p>{@link UnifiedConfigurationHelper} pins the {@code Environment} of whichever
 * ApplicationContext last constructed it into a static field, used as a legacy-configuration
 * fallback. Spring caches and reuses test contexts, so that reference otherwise survives past the
 * test class that created it and leaks into whichever test runs next in the same JVM — including
 * plain-POJO tests that build a {@link Camunda} without any Spring context at all, silently
 * corrupting their legacy-property fallback with a stale environment from an unrelated test.
 */
public class UnifiedConfigurationHelperResetListener extends AbstractTestExecutionListener {

  @Override
  public void afterTestClass(final TestContext testContext) {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }
}

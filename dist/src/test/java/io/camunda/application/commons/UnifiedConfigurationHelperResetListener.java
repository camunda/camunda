/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationHelper;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * Binds {@link UnifiedConfigurationHelper}'s static {@code environment} reference to the {@link
 * TestContext} that is currently running, and clears it again after every Spring test class
 * (including {@code @Nested} classes, which each get their own {@link TestContext}).
 *
 * <p>{@link UnifiedConfigurationHelper} pins the {@code Environment} of whichever
 * ApplicationContext constructed it into a static field, used as a legacy-configuration fallback.
 * That makes the fallback global mutable state with two failure modes. Without the clear, the
 * reference survives past the test class that created it and leaks into whichever test runs next in
 * the same JVM, including plain-POJO tests that build a {@link Camunda} without any Spring context
 * at all. Without the bind, a Spring test class that reuses a cached context never constructs the
 * helper again, so it runs with no environment and silently resolves no legacy property at all.
 *
 * <p>This duplicates the listener of the {@code configuration} module. Test classes are not
 * published, so a module with both Spring tests and plain-POJO configuration tests has to register
 * its own.
 */
public class UnifiedConfigurationHelperResetListener extends AbstractTestExecutionListener {

  @Override
  public void prepareTestInstance(final TestContext testContext) {
    UnifiedConfigurationHelper.setCustomEnvironment(
        testContext.getApplicationContext().getEnvironment());
  }

  @Override
  public void afterTestClass(final TestContext testContext) {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }
}

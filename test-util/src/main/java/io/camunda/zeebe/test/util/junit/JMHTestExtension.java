/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.junit;

import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest.None;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.ReflectionUtils;

/**
 * Injects a pre-configured {@link JMHTestCase} test case based on the {@link JMHTest} annotation.
 */
final class JMHTestExtension implements ParameterResolver {

  @Override
  public boolean supportsParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    return ReflectionUtils.isAssignableTo(
        parameterContext.getParameter().getType(), JMHTestCase.class);
  }

  @Override
  public Object resolveParameter(
      final ParameterContext parameterContext, final ExtensionContext extensionContext)
      throws ParameterResolutionException {
    final var testMethod = extensionContext.getRequiredTestMethod();
    final var annotation = testMethod.getAnnotation(JMHTest.class);

    return JMHTestCase.of(findBenchmarkClass(extensionContext, annotation), annotation.value());
  }

  private Class<?> findBenchmarkClass(final ExtensionContext context, final JMHTest annotation) {
    final var userDefined = annotation.jmhClass();
    if (None.class.equals(userDefined)) {
      return context.getRequiredTestClass();
    }

    return userDefined;
  }
}

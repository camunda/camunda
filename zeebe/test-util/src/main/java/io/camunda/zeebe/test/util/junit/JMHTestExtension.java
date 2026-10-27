/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.junit;

import io.camunda.zeebe.test.util.jmh.JMHTestCase;
import io.camunda.zeebe.test.util.junit.JMHTest.None;
import io.camunda.zeebe.util.FileUtil;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.platform.commons.util.ReflectionUtils;
import org.openjdk.jmh.results.format.ResultFormatType;

/**
 * Injects a pre-configured {@link JMHTestCase} test case based on the {@link JMHTest} annotation.
 */
final class JMHTestExtension implements ParameterResolver {
  private static final Path RESULTS_DIR;

  static {
    final var resultsDir = System.getenv("ZEEBE_PERFORMANCE_TEST_RESULTS_DIR");
    if (resultsDir != null) {
      RESULTS_DIR = Path.of(resultsDir);
      try {
        FileUtil.ensureDirectoryExists(RESULTS_DIR);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    } else {
      RESULTS_DIR = null;
    }
  }

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

    final var testCase =
        JMHTestCase.of(findBenchmarkClass(extensionContext, annotation), annotation.value());

    if (RESULTS_DIR != null) {
      testCase.withOptions(
          opt -> {
            final var resultFile =
                "%s#%s.txt"
                    .formatted(
                        extensionContext.getRequiredTestClass().getName(), testMethod.getName());
            final var resultPath = RESULTS_DIR.resolve(resultFile);
            opt.resultFormat(ResultFormatType.TEXT);
            opt.result(resultPath.toString());
          });
    }

    return testCase;
  }

  private Class<?> findBenchmarkClass(final ExtensionContext context, final JMHTest annotation) {
    final var userDefined = annotation.jmhClass();
    if (None.class.equals(userDefined)) {
      return context.getRequiredTestClass();
    }

    return userDefined;
  }
}

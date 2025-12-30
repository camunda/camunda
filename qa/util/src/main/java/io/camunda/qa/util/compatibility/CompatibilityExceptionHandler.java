/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.compatibility;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles exceptions during compatibility test execution.
 *
 * <p>This handler catches runtime linkage errors (NoSuchMethodError, NoSuchFieldError) that occur
 * when tests compiled against newer APIs run with older client versions. Instead of failing the
 * test, it gracefully aborts the test with a descriptive message.
 *
 * <p>This allows the same test suite to run across multiple client versions, automatically skipping
 * tests that require APIs not available in the current version.
 *
 * <p>Example scenario:
 *
 * <ul>
 *   <li>Test compiles against client version 8.9.0 (latest APIs available)
 *   <li>Test runs with client version 8.8.0 (older APIs)
 *   <li>Test calls {@code resourcePropertyName()} method added in 8.9.0
 *   <li>NoSuchMethodError is thrown at runtime
 *   <li>This handler catches it and aborts the test with clear message
 * </ul>
 *
 * @see CompatibilityTestExtension
 */
public final class CompatibilityExceptionHandler implements TestExecutionExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CompatibilityExceptionHandler.class);
  private static final String CLIENT_VERSION_PROPERTY = "camunda.client.version";

  @Override
  public void handleTestExecutionException(
      final ExtensionContext context, final Throwable throwable) throws Throwable {

    // Handle NoSuchMethodError - method not available in client version
    if (throwable instanceof NoSuchMethodError) {
      handleApiNotAvailable(context, (NoSuchMethodError) throwable, "method");
      return;
    }

    // Handle NoSuchFieldError - field/enum not available in client version
    if (throwable instanceof NoSuchFieldError) {
      handleApiNotAvailable(context, (NoSuchFieldError) throwable, "field");
      return;
    }

    // Handle IncompatibleClassChangeError - API signature changed
    if (throwable instanceof IncompatibleClassChangeError) {
      handleApiNotAvailable(context, (IncompatibleClassChangeError) throwable, "API");
      return;
    }

    // For other exceptions, check if caused by linkage error
    final var cause = throwable.getCause();
    if (cause instanceof NoSuchMethodError
        || cause instanceof NoSuchFieldError
        || cause instanceof IncompatibleClassChangeError) {
      handleTestExecutionException(context, cause);
      return;
    }

    // Not a compatibility issue - rethrow
    throw throwable;
  }

  private void handleApiNotAvailable(
      final ExtensionContext context, final Throwable error, final String apiType) {

    final var clientVersion = System.getProperty(CLIENT_VERSION_PROPERTY, "unknown");
    final var testName = context.getDisplayName();
    final var missingApi = extractApiName(error.getMessage());

    final var message =
        String.format(
            "Test '%s' requires %s '%s' not available in client version '%s'. "
                + "This test will be skipped. To run this test, use a newer client version.",
            testName, apiType, missingApi, clientVersion);

    LOG.info(message);
    LOG.debug("Compatibility error details", error);

    // Abort the test (marks it as skipped, not failed)
    throw new TestAbortedException(message, error);
  }

  private String extractApiName(final String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "unknown";
    }

    // NoSuchMethodError format: 'ReturnType ClassName.methodName(params)'
    // NoSuchFieldError format: 'FieldName'
    // Extract the relevant part
    final var parts = errorMessage.split("'");
    if (parts.length > 1) {
      return parts[1];
    }

    // Fallback: try to extract class.method or just return the message
    final var dotIndex = errorMessage.lastIndexOf('.');
    if (dotIndex > 0) {
      return errorMessage.substring(dotIndex + 1);
    }

    return errorMessage;
  }
}

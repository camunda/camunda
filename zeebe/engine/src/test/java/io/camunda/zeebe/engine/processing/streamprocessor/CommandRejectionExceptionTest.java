/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.ForbiddenException;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * Tests for the {@link CommandRejectionException} mechanism, which provides automatic rollback of
 * state changes and discarding of follow-up events when a command should be rejected.
 *
 * <p>These tests validate the infrastructure without modifying production processors.
 */
public class CommandRejectionExceptionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Test
  public void shouldBeStackless() {
    // given
    final CommandRejectionException exception =
        new CommandRejectionException(RejectionType.INVALID_STATE, "Test rejection");

    // when
    final StackTraceElement[] stackTrace = exception.getStackTrace();

    // then
    assertThat(stackTrace).isEmpty();
  }

  @Test
  public void shouldReturnSameInstanceFromFillInStackTrace() {
    // given
    final CommandRejectionException exception =
        new CommandRejectionException(RejectionType.INVALID_STATE, "Test rejection");

    // when
    final Throwable result = exception.fillInStackTrace();

    // then - should return same instance without filling stack trace
    assertThat(result).isSameAs(exception);
    assertThat(exception.getStackTrace()).isEmpty();
  }

  @Test
  public void shouldContainRejectionTypeAndReason() {
    // given
    final RejectionType expectedType = RejectionType.NOT_FOUND;
    final String expectedReason = "Resource not found";

    // when
    final CommandRejectionException exception =
        new CommandRejectionException(expectedType, expectedReason);

    // then
    assertThat(exception.getRejectionType()).isEqualTo(expectedType);
    assertThat(exception.getMessage()).isEqualTo(expectedReason);
  }

  /**
   * Test that validates the ForbiddenException inheritance. This ensures backwards compatibility -
   * existing code throwing ForbiddenException continues to work.
   */
  @Test
  public void forbiddenExceptionShouldBeACommandRejectionException() {
    // given
    final ForbiddenException forbiddenException =
        new ForbiddenException(
            new AuthorizationRequest(
                    null,
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    PermissionType.CREATE,
                    "<default>")
                .addResourceId("test"));

    // then - instanceof check works via inheritance
    assertThat(forbiddenException).isInstanceOf(CommandRejectionException.class);
    assertThat(forbiddenException.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(forbiddenException.getStackTrace()).isEmpty(); // inherited stackless behavior
  }

  /**
   * This test validates that rejections work correctly for different RejectionTypes. While we can't
   * test state rollback without a test processor, we can validate the exception properties.
   */
  @Test
  public void shouldSupportAllRejectionTypes() {
    // Test each rejection type to ensure they all work
    final RejectionType[] rejectionTypes = {
      RejectionType.INVALID_ARGUMENT,
      RejectionType.NOT_FOUND,
      RejectionType.ALREADY_EXISTS,
      RejectionType.INVALID_STATE,
      RejectionType.PROCESSING_ERROR,
      RejectionType.EXCEEDED_BATCH_RECORD_SIZE,
      RejectionType.FORBIDDEN
    };

    for (final RejectionType type : rejectionTypes) {
      final CommandRejectionException exception =
          new CommandRejectionException(type, "Test reason for " + type);

      assertThat(exception.getRejectionType())
          .as("Rejection type should be preserved for %s", type)
          .isEqualTo(type);
      assertThat(exception.getMessage()).contains(type.toString());
      assertThat(exception.getStackTrace())
          .as("Exception should be stackless for %s", type)
          .isEmpty();
    }
  }

  /**
   * Integration test that validates the exception can be caught and handled properly in error
   * handling code. This simulates what happens in processor tryHandleError() methods.
   */
  @Test
  public void shouldBeCatchableAsCommandRejectionException() {
    // given
    final CommandRejectionException exception =
        new CommandRejectionException(RejectionType.NOT_FOUND, "Test reason");

    // when - simulate error handling code
    final boolean caughtAsCommandRejection = exception instanceof CommandRejectionException;
    final boolean caughtAsRuntimeException = exception instanceof RuntimeException;

    // then
    assertThat(caughtAsCommandRejection).isTrue();
    assertThat(caughtAsRuntimeException).isTrue();
  }

  /**
   * Test that ForbiddenException can be caught as CommandRejectionException via polymorphism. This
   * validates that error handlers can catch the base type and handle all rejection exceptions
   * uniformly.
   */
  @Test
  public void forbiddenExceptionShouldBeCatchableAsCommandRejectionException() {
    // given
    final Throwable exception =
        new ForbiddenException(
            new AuthorizationRequest(
                    null,
                    AuthorizationResourceType.PROCESS_DEFINITION,
                    PermissionType.CREATE,
                    "<default>")
                .addResourceId("test"));

    // when - simulate error handling with instanceof check
    if (exception instanceof CommandRejectionException commandRejection) {
      // then - polymorphism works, we can access base class methods
      assertThat(commandRejection.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
      assertThat(commandRejection.getMessage()).isNotEmpty();
      return;
    }

    throw new AssertionError("ForbiddenException should be catchable as CommandRejectionException");
  }
}

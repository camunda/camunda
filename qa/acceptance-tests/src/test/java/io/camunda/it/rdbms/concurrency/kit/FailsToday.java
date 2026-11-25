/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.opentest4j.TestAbortedException;

/**
 * A scenario that reproduces a known problem on some databases: the test states the behaviour we
 * want, and fails there today. Repeat it for different reasons per database.
 *
 * <p>On those databases a failed assertion ({@link AssertionError}) is the expected outcome: the
 * run reports the test as aborted (skipped) with the reason, and the suite stays green. If the test
 * passes there, it fails with "fixed? remove @FailsToday", so the marker cannot outlive the
 * problem. Any other exception, e.g. a {@link ThreadControl#stepTo} that does not get where the
 * scenario expects, still fails the test: the scenario itself no longer runs as intended.
 *
 * <pre>{@code
 * @TestTemplate
 * @FailsToday(on = {MYSQL, MARIADB}, reason = "the cleanup's next-key locks at REPEATABLE READ")
 * @FailsToday(on = MSSQL, reason = "the DELETE escalates to a table lock")
 * void shouldNotBlockTheFlushOfTheSamePartition(...)
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(FailsToday.List.class)
public @interface FailsToday {

  /** The databases the test fails on today. */
  Database[] on();

  /** Why it fails there, as the failure shows it. */
  String reason();

  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  @interface List {
    FailsToday[] value();
  }

  /** Turns the expected failure into an aborted test, and an unexpected pass into a failure. */
  final class Extension implements InvocationInterceptor {

    @Override
    public void interceptTestTemplateMethod(
        final Invocation<Void> invocation,
        final ReflectiveInvocationContext<Method> invocationContext,
        final ExtensionContext context)
        throws Throwable {
      final Database database = database(context);
      final var marker =
          AnnotationSupport.findRepeatableAnnotations(
                  context.getRequiredTestMethod(), FailsToday.class)
              .stream()
              .filter(failsToday -> Arrays.asList(failsToday.on()).contains(database))
              .findFirst();
      if (marker.isEmpty()) {
        invocation.proceed();
        return;
      }
      try {
        invocation.proceed();
      } catch (final AssertionError expected) {
        throw new TestAbortedException(
            "fails today on " + database + " as expected: " + marker.get().reason(), expected);
      }
      throw new AssertionError(
          "passes on "
              + database
              + " although marked @FailsToday(\""
              + marker.get().reason()
              + "\"): fixed? Remove the marker for "
              + database);
    }

    /** The database of this invocation: its display name is the test application's name. */
    private static Database database(final ExtensionContext context) {
      final Database[] matches =
          Arrays.stream(Database.values())
              .filter(d -> context.getDisplayName().contains(d.testApplication))
              .toArray(Database[]::new);
      if (matches.length != 1) {
        throw new IllegalStateException("no database in " + context.getDisplayName());
      }
      return matches[0];
    }
  }
}

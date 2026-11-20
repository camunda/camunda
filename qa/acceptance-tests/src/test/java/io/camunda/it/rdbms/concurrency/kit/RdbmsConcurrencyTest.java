/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.concurrency.kit;

import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

/**
 * Marks a concurrency kit test class. Its {@code @TestTemplate} methods get a {@code
 * CamundaRdbmsTestApplication} per database they run on (tag {@code rdbms}, Maven profile {@code
 * rdbms}).
 *
 * <p>{@link #databases()} declares the databases the case applies to. Which ones run:
 *
 * <ul>
 *   <li>environment variable {@code RDBMS_KIT_DB} not set: none, the class is skipped. The kit is
 *       opt-in, so the regular {@code rdbms} test runs (e.g. in CI) do not start it;
 *   <li>{@code RDBMS_KIT_DB} set, e.g. {@code H2}, {@code MSSQL} or {@code MYSQL,POSTGRES}: those
 *       of them the case applies to; the class is skipped if there are none.
 * </ul>
 *
 * The applications and their containers are shared by all test classes of a run.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("rdbms")
@ExtendWith({RdbmsConcurrencyTest.Databases.class, FailsToday.Extension.class})
public @interface RdbmsConcurrencyTest {

  /** The databases this case applies to. */
  Database[] databases() default {
    Database.H2,
    Database.POSTGRES,
    Database.MYSQL,
    Database.MARIADB,
    Database.ORACLE,
    Database.MSSQL
  };

  /** Runs the test templates of a class on the databases selected as described above. */
  final class Databases
      implements TestTemplateInvocationContextProvider,
          BeforeAllCallback,
          AfterAllCallback,
          ExecutionCondition {

    private final Map<Class<?>, CamundaRdbmsInvocationContextProviderExtension> byTestClass =
        new ConcurrentHashMap<>();

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(final ExtensionContext context) {
      final var declared = declared(context);
      return selected(context).isEmpty()
          ? ConditionEvaluationResult.disabled(
              System.getenv("RDBMS_KIT_DB") == null
                  ? "opt-in: set RDBMS_KIT_DB, e.g. H2 or MYSQL,POSTGRES"
                  : "applies only to "
                      + declared
                      + ", RDBMS_KIT_DB is "
                      + System.getenv("RDBMS_KIT_DB"))
          : ConditionEvaluationResult.enabled("runs on " + selected(context));
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
      applications(context).beforeAll(context);
    }

    @Override
    public void afterAll(final ExtensionContext context) {
      applications(context).afterAll(context);
    }

    @Override
    public boolean supportsTestTemplate(final ExtensionContext context) {
      return applications(context).supportsTestTemplate(context);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(
        final ExtensionContext context) {
      return applications(context).provideTestTemplateInvocationContexts(context);
    }

    private CamundaRdbmsInvocationContextProviderExtension applications(
        final ExtensionContext context) {
      return byTestClass.computeIfAbsent(
          context.getRequiredTestClass(),
          testClass ->
              new CamundaRdbmsInvocationContextProviderExtension(
                  selected(context).stream().map(d -> d.testApplication).toArray(String[]::new)));
    }

    private static List<Database> selected(final ExtensionContext context) {
      final var declared = declared(context);
      final String env = System.getenv("RDBMS_KIT_DB");
      if (env == null || env.isBlank()) {
        return List.of();
      }
      return Arrays.stream(env.split(","))
          .map(name -> Database.valueOf(name.strip().toUpperCase(Locale.ROOT)))
          .filter(declared::contains)
          .toList();
    }

    private static List<Database> declared(final ExtensionContext context) {
      final var annotation =
          context.getRequiredTestClass().getAnnotation(RdbmsConcurrencyTest.class);
      return List.of(annotation.databases());
    }
  }
}

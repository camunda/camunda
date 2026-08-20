/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Ensures that service-layer classes do not call the executor-less static factory methods {@link
 * CompletableFuture#supplyAsync(java.util.function.Supplier)} or {@link
 * CompletableFuture#runAsync(Runnable)}.
 *
 * <p>The service layer offloads authorization and secondary-storage reads onto a managed, bounded
 * executor obtained from {@code ApiServicesExecutorProvider.getExecutor()}. That executor is the
 * single chokepoint responsible for propagating request-scoped context (e.g. the resolved physical
 * tenant) onto worker threads. The executor-less overloads submit tasks to the common {@link
 * java.util.concurrent.ForkJoinPool}, which bypasses context propagation and causes the
 * request-scoped state to be invisible on the worker thread.
 *
 * <p>Always pass the managed executor explicitly:
 *
 * <pre>{@code
 * // UNSAFE: task runs on the common ForkJoinPool, loses request-scoped context
 * CompletableFuture.supplyAsync(() -> searchClient.search(query));
 *
 * // SAFE: task runs on the managed executor, context is propagated
 * CompletableFuture.supplyAsync(() -> searchClient.search(query), executorProvider.getExecutor());
 * }</pre>
 */
@AnalyzeClasses(packages = "io.camunda.service", importOptions = DoNotIncludeTestsOrTestJars.class)
public final class ServiceAsyncExecutorArchTest {

  /**
   * R1 — service-layer classes must not call the executor-less overloads of {@link
   * CompletableFuture#supplyAsync} or {@link CompletableFuture#runAsync}.
   *
   * <p>Calls that pass an {@link Executor} as a parameter are explicitly allowed.
   *
   * <p>Scope: this rule only covers {@code io.camunda.service..}, where the managed executor
   * chokepoint ({@code ApiServicesExecutorProvider}) lives. It does not catch the same pattern in
   * other modules (e.g. {@code authentication}, {@code gateways}); if async authorization reads are
   * ever offloaded from there, extend the scope accordingly.
   */
  @ArchTest
  public static final ArchRule
      R1_SERVICE_CLASSES_MUST_NOT_CALL_EXECUTOR_LESS_COMPLETABLE_FUTURE_FACTORY_METHODS =
          classes()
              .that()
              .resideInAPackage("io.camunda.service..")
              .should(notCallExecutorLessAsyncFactoryMethods())
              .because(
                  "service-layer async reads must run on the managed/bounded executor obtained from"
                      + " ApiServicesExecutorProvider.getExecutor() so that request-scoped context"
                      + " (e.g. the resolved physical tenant) is propagated to the worker thread;"
                      + " the executor-less overloads use the common ForkJoinPool and bypass it");

  private static ArchCondition<JavaClass> notCallExecutorLessAsyncFactoryMethods() {
    return new ArchCondition<>(
        "not call CompletableFuture.supplyAsync(Supplier) or CompletableFuture.runAsync(Runnable)"
            + " without an Executor argument") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        item.getMethodCallsFromSelf().stream()
            .filter(ServiceAsyncExecutorArchTest::isExecutorLessAsyncFactoryCall)
            .forEach(
                call ->
                    events.add(
                        violated(
                            item,
                            String.format(
                                "%s calls CompletableFuture.%s() without an Executor at %s"
                                    + " — pass ApiServicesExecutorProvider.getExecutor() as the"
                                    + " executor argument so request-scoped context propagates to"
                                    + " the worker thread",
                                item.getName(), call.getName(), call.getSourceCodeLocation()))));
      }
    };
  }

  private static boolean isExecutorLessAsyncFactoryCall(final JavaMethodCall call) {
    final String name = call.getName();
    if (!"supplyAsync".equals(name) && !"runAsync".equals(name)) {
      return false;
    }
    if (!call.getTargetOwner().isAssignableTo(CompletableFuture.class)) {
      return false;
    }
    // The call is executor-less if none of its parameters is assignable to Executor
    return call.getTarget().getRawParameterTypes().stream()
        .noneMatch(param -> param.isAssignableTo(Executor.class));
  }
}

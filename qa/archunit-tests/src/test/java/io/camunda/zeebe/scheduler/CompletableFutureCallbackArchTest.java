/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Ensures that actor-scoped code does not use synchronous callback methods on {@link
 * CompletableFuture}.
 *
 * <p>Synchronous callback methods ({@code whenComplete}, {@code thenApply}, {@code handle}, etc.)
 * execute the callback on whichever thread completes the future. When actor code chains a callback
 * on a {@link CompletableFuture} returned by an external service (e.g. a backup store backed by S3,
 * the Raft layer, or a messaging service), the callback may run on a non-actor thread. If that
 * callback accesses actor-owned state (RocksDB column families, shared data structures, etc.), the
 * result is a race condition that can cause data corruption or a JVM crash (SIGSEGV).
 *
 * <p>The fix is to always use the async variant with the actor executor:
 *
 * <pre>{@code
 * // UNSAFE: callback runs on the completing thread (e.g. Netty I/O thread)
 * backupStore.save(backup).whenComplete((result, error) -> { ... });
 *
 * // SAFE: callback is dispatched to the actor thread
 * backupStore.save(backup).whenCompleteAsync((result, error) -> { ... }, executor);
 * }</pre>
 *
 * <p>A class is considered "actor-scoped" if it extends {@link Actor} or declares a field of type
 * {@link ConcurrencyControl} (the actor scheduling interface) or any subtype (for example, {@link
 * ActorControl}).
 *
 * @see <a href="https://github.com/camunda/camunda/pull/47359">PR #47359</a> for an example of this
 *     bug class causing a JVM crash.
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public class CompletableFutureCallbackArchTest {

  @ArchTest
  static final ArchRule ACTOR_CODE_SHOULD_USE_ASYNC_COMPLETABLE_FUTURE_CALLBACKS =
      classes()
          .that(useActorConcurrencyControl())
          .should(notCallSyncCompletableFutureCallbacks())
          .because(
              "synchronous callback methods on CompletableFuture execute on the completing "
                  + "thread (e.g. a Netty I/O thread), not the actor thread. Use the async "
                  + "variant with the actor executor instead, e.g. "
                  + ".whenCompleteAsync(callback, concurrencyControl)");

  /**
   * Synchronous callback methods on {@link CompletableFuture} / {@link CompletionStage} that
   * execute the callback on the completing thread. Each of these has an {@code *Async} counterpart
   * that accepts an {@link java.util.concurrent.Executor}.
   */
  private static final Set<String> SYNC_CALLBACK_METHODS =
      Set.of(
          "whenComplete",
          "handle",
          "thenApply",
          "thenAccept",
          "thenRun",
          "thenCompose",
          "exceptionally",
          "acceptEither",
          "applyToEither",
          "runAfterBoth",
          "runAfterEither",
          "thenAcceptBoth",
          "thenCombine");

  private static DescribedPredicate<JavaClass> useActorConcurrencyControl() {
    return new DescribedPredicate<>("use actor concurrency control") {
      @Override
      public boolean test(final JavaClass javaClass) {
        if (javaClass.isAssignableTo(Actor.class)) {
          return true;
        }
        // Match classes with a ConcurrencyControl or ActorControl field, but exclude fields whose
        // type extends Actor — those are references to other actors, not indicators that THIS class
        // is actor-scoped. For example, Broker has a BrokerStartupActor field but Broker itself is
        // not actor-scoped.
        return javaClass.getAllFields().stream()
            .anyMatch(
                field -> {
                  final var fieldType = field.getRawType();
                  return fieldType.isAssignableTo(ConcurrencyControl.class)
                      && !fieldType.isAssignableTo(Actor.class);
                });
      }
    };
  }

  private static ArchCondition<JavaClass> notCallSyncCompletableFutureCallbacks() {
    return new ArchCondition<>(
        "not call synchronous callback methods on CompletableFuture or CompletionStage") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {
        item.getMethodCallsFromSelf().stream()
            .filter(CompletableFutureCallbackArchTest::isSyncCallbackOnCompletableFuture)
            .forEach(
                call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            item,
                            String.format(
                                "%s calls %s.%s() at %s — use %sAsync() with the actor executor instead",
                                item.getName(),
                                call.getTargetOwner().getSimpleName(),
                                call.getName(),
                                call.getSourceCodeLocation(),
                                call.getName()))));
      }
    };
  }

  private static boolean isSyncCallbackOnCompletableFuture(final JavaMethodCall call) {
    if (!SYNC_CALLBACK_METHODS.contains(call.getName())) {
      return false;
    }
    final var targetOwner = call.getTargetOwner();
    return targetOwner.isAssignableTo(CompletableFuture.class)
        || targetOwner.isAssignableTo(CompletionStage.class);
  }
}

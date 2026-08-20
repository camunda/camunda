/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import java.util.Set;

/**
 * Architecture test ensuring that state modifications follow the event sourcing pattern.
 *
 * <p>This test enforces the event sourcing pattern defined in ZEP004, where state changes should be
 * made through applying events. This architectural constraint ensures deterministic replay of the
 * event log and maintains consistency across replicas.
 *
 * <h2>Allowed State Modifiers</h2>
 *
 * <ul>
 *   <li><b>Event Appliers</b> ({@code io.camunda.zeebe.engine.state.appliers.*}) - Primary
 *       mechanism for state changes
 *   <li><b>Migration Classes</b> ({@code io.camunda.zeebe.engine.state.migration.*}) - One-time
 *       schema/data migrations
 *   <li><b>Whitelisted Methods</b> - Specific methods documented and reviewed as safe exceptions
 * </ul>
 *
 * @see <a
 *     href="https://github.com/zeebe-io/enhancements/blob/master/ZEP004-wf-stream-processing.md">ZEP004
 *     - Workflow Stream Processing</a>
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class StateModificationArchTest {

  /**
   * Known exceptions to the state modification rule.
   *
   * <p>These specific methods are explicitly whitelisted because they have been reviewed and
   * determined to be safe despite modifying state outside of event appliers. Each exception is
   * documented with the reason for its inclusion.
   *
   * <p>Format: "fully.qualified.ClassName.methodName"
   */
  private static final Set<String> WHITELISTED_METHODS =
      Set.of(
          /*
           * Helper classes in 'state.appliers' that do NOT implement TypedEventApplier directly.
           * These are used exclusively by TypedEventApplier implementations to extract
           * reusable event-applying logic. They are safe because:
           * - They live in the state.appliers package (part of the event applier infrastructure)
           * - They are only called from actual TypedEventApplier implementations
           */
          "io.camunda.zeebe.engine.state.appliers.BufferedStartMessageEventStateApplier.removeProcessInstanceMessageLock",
          "io.camunda.zeebe.engine.state.appliers.EventSubProcessInterruptionMarker.markInstanceIfInterrupted",

          /*
           * ProcessInstanceCreationCreateWithAwaitingResultProcessor.createProcessInstance():
           * Stores request metadata (requestId, streamId) for CREATE_WITH_AWAITING_RESULT.
           * Safe because: this metadata is only used for response routing back to the original
           * requester and is never read during process execution or replay. The process behaves
           * identically whether this metadata exists or not.
           */
          "io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateWithAwaitingResultProcessor.createProcessInstance",

          /*
           * State mutating methods calling other mutating state methods:
           *
           * PRINCIPLE: It's acceptable for mutating state methods to call other mutating state
           * methods to maintain consistency between tightly coupled data. However, read methods
           * should NEVER call mutating methods.
           *
           * RECOMMENDATION: Structure the logic so that event appliers orchestrate these calls
           * instead of having state methods call each other directly. This makes the state
           * transitions more explicit and easier to understand.
           */
          "io.camunda.zeebe.engine.state.instance.DbElementInstanceState.createInstance",
          "io.camunda.zeebe.engine.state.instance.DbElementInstanceState.removeInstance");

  /**
   * Identifies methods that directly modify state.
   *
   * <p>A method is considered to directly modify state if:
   *
   * <ul>
   *   <li>It belongs to a class in the {@code io.camunda.zeebe.engine.state.mutable} package
   *   <li>It is NOT defined in {@link MutableProcessingState}
   * </ul>
   *
   * <p>{@link MutableProcessingState} methods are excluded because they are top-level accessors
   * that provide references to individual mutable state classes and the key generator. These
   * methods are used pervasively by processors, behaviors, and infrastructure code throughout the
   * engine for both reading state and generating keys during command processing.
   */
  private static final DescribedPredicate<JavaMethod> METHODS_THAT_DIRECTLY_MODIFY_STATE =
      new DescribedPredicate<>("directly mutate state") {
        @Override
        public boolean test(final JavaMethod method) {
          // method is from a mutable state class (from the 'state.mutable' package)
          return method
                  .getOwner()
                  .getPackageName()
                  .startsWith("io.camunda.zeebe.engine.state.mutable")
              // but not from the MutableProcessingState interface, which is a special case
              // of top-level accessor methods that are used widely across the engine and
              // do not themselves mutate state
              && !method.getOwner().isAssignableTo(MutableProcessingState.class);
        }
      };

  /**
   * Identifies methods that are permitted to modify state.
   *
   * <p>The following are allowed to call state-modifying methods:
   *
   * <ul>
   *   <li>Event appliers implementing {@code TypedEventApplier}
   *   <li>Migration classes in {@code io.camunda.zeebe.engine.state.migration}
   *   <li>Explicitly whitelisted methods with documented exceptions
   * </ul>
   */
  private static final DescribedPredicate<JavaMethod> METHODS_ALLOWED_TO_MODIFY_STATE =
      new DescribedPredicate<>("are allowed to mutate state") {
        @Override
        public boolean test(final JavaMethod method) {
          final var owner = method.getOwner();
          // Allowed if method is from an event applier class
          return owner.isAssignableTo(TypedEventApplier.class)
              // or if it's from a migration class
              || owner.getPackageName().startsWith("io.camunda.zeebe.engine.state.migration")
              // or if it's a whitelisted method
              || WHITELISTED_METHODS.contains(owner.getFullName() + "." + method.getName());
        }
      };

  /**
   * Ensures that only authorized methods modify engine state.
   *
   * <p>This rule prevents accidental state mutations outside of event appliers, which could lead
   * to:
   *
   * <ul>
   *   <li>Non-deterministic replay of the event log
   *   <li>Inconsistent state across replicas
   *   <li>Difficult-to-debug state corruption issues
   * </ul>
   */
  @ArchTest
  static final ArchRule ONLY_ALLOWED_STATE_MODIFICATIONS =
      methods()
          .that(METHODS_THAT_DIRECTLY_MODIFY_STATE)
          .should()
          .onlyBeCalled()
          .byMethodsThat(METHODS_ALLOWED_TO_MODIFY_STATE)
          .because(
              """
                  State modifications must go through event appliers to maintain the event sourcing pattern (ZEP004).
                  This ensures deterministic replay and consistency across replicas.
                  If you need to modify state:
                   1. PREFERRED: Create an appropriate event and event applier
                   2. ONLY if a mutating state method needs to call another mutating state method for tightly
                      coupled data consistency, document and whitelist in StateModificationArchTest.WHITELISTED_METHODS
                  Note: Whitelisting should NOT be used to bypass the event sourcing pattern from processors or behavior classes.""");
}

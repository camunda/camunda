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
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.camunda.zeebe.engine.state.TypedEventApplier;
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
           * ProcessInstanceCreationCreateWithAwaitingResultProcessor.createProcessInstance():
           * Stores request metadata (requestId, streamId) for CREATE_WITH_AWAITING_RESULT.
           * Safe because: this metadata is only used for response routing back to the original
           * requester and is never read during process execution or replay. The process behaves
           * identically whether this metadata exists or not.
           */
          "io.camunda.zeebe.engine.processing.processinstance.ProcessInstanceCreationCreateWithAwaitingResultProcessor.createProcessInstance");

  /**
   * Identifies methods that directly modify state.
   *
   * <p>A method is considered to directly modify state if:
   *
   * <ul>
   *   <li>It belongs to a class in the {@code io.camunda.zeebe.engine.state.mutable} package
   *   <li>It is NOT a getter method for accessing other state classes
   *   <li>It is NOT the {@code getKeyGenerator()} method
   * </ul>
   *
   * <p>Methods that return other state objects or utilities are excluded because they don't mutate
   * state themselves - they provide access to other components that may mutate state.
   */
  private static final DescribedPredicate<JavaMethod> METHODS_THAT_DIRECTLY_MODIFY_STATE =
      new DescribedPredicate<>("directly mutate state") {
        @Override
        public boolean test(final JavaMethod method) {
          // Method must be in a mutable state interface
          return method
                  .getOwner()
                  .getPackageName()
                  .startsWith("io.camunda.zeebe.engine.state.mutable")
              // Exclude getter methods that return other state classes (e.g., getJobState())
              && !(method.getName().startsWith("get") && method.getName().endsWith("State"))
              // Exclude the getKeyGenerator() method which returns a utility object
              && !method.getName().equals("getKeyGenerator");
        }
      };

  /**
   * Identifies methods that are permitted to modify state.
   *
   * <p>The following are allowed to call state-modifying methods:
   *
   * <ul>
   *   <li>Event appliers in {@code io.camunda.zeebe.engine.state.appliers}
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
          return isEventApplier(owner)
              // or if it's from a migration class
              || owner.getPackageName().startsWith("io.camunda.zeebe.engine.state.migration")
              // or if it's a whitelisted method
              || WHITELISTED_METHODS.contains(owner.getFullName() + "." + method.getName());
        }

        /**
         * Checks if a class is an event applier.
         *
         * <p>Event appliers are located in the {@code io.camunda.zeebe.engine.state.appliers}
         * package or implement the {@link TypedEventApplier} interface.
         */
        private boolean isEventApplier(final JavaClass owner) {
          return owner.getPackageName().startsWith("io.camunda.zeebe.engine.state.appliers")
              || owner.isAssignableTo(TypedEventApplier.class);
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
                  If you need to modify state, either:
                   1. Create an appropriate event and event applier, or
                   2. Document and whitelist your exception in StateModificationArchTest.WHITELISTED_METHODS""");
}

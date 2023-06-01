/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * This architecture test ensures that only event appliers mutate state. This enforces the event
 * sourcing pattern, where all state changes are made by applying events.
 *
 * @see <a
 *     href=https://github.com/zeebe-io/enhancements/blob/master/ZEP004-wf-stream-processing.md>ZEP004</a>
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.engine",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class StateModificationTest {

  /**
   * Describes all methods that directly mutate state. This is all methods in the mutable package,
   * except getters for other state classes and the key generator. These don't mutate state
   * directly, and are only there to provide access other related state classes.
   */
  private static final DescribedPredicate<JavaMethod> DIRECTLY_MUTATE_STATE =
      new DescribedPredicate<>("directly mutate state") {
        @Override
        public boolean test(JavaMethod input) {
          return input
                  .getOwner()
                  .getPackageName()
                  .startsWith("io.camunda.zeebe.engine.state.mutable")
              // Except getters for other state classes and the key generator
              && !(input.getName().startsWith("get") && input.getName().endsWith("State"))
              && !input.getName().equals("getKeyGenerator");
        }
      };

  /**
   * Describes methods allowed to mutate state. This is mainly appliers, but we also allow
   * transitive usage from other methods in the mutable package. Additionally, we allow migration
   * classes to mutate state, as they are only used during migration and not during normal
   * processing.
   */
  private static final DescribedPredicate<JavaMethod> ARE_ALLOWED_TO_MUTATE_STATE =
      new DescribedPredicate<>("are allowed to mutate state") {
        @Override
        public boolean test(JavaMethod input) {
          final var pkg = input.getOwner().getPackageName();
          return pkg.startsWith("io.camunda.zeebe.engine.state.appliers")
              // or transitive usage in the mutable package
              || pkg.startsWith("io.camunda.zeebe.engine.state.mutable")
              // or migration classes which we can ignore as a special case
              || pkg.startsWith("io.camunda.zeebe.engine.state.migration");
        }
      };

  @ArchTest()
  public static final ArchRule ONLY_ALLOWED_STATE_MODIFICATIONS =
      methods()
          .that(DIRECTLY_MUTATE_STATE)
          .should()
          .onlyBeCalled()
          .byMethodsThat(ARE_ALLOWED_TO_MUTATE_STATE);
}

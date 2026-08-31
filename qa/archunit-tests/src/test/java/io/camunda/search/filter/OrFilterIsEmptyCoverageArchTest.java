/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static com.tngtech.archunit.lang.SimpleConditionEvent.satisfied;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every {@link OrFilter} implementation must reference each of its own criteria record components
 * from {@code isEmpty()}, since {@link OrFilter#hasEmptyOrFilter()} relies on it to decide whether
 * a {@code $or} group carries no criteria (see https://github.com/camunda/camunda/issues/60407). A
 * component missing from {@code isEmpty()} would make that field invisible to the empty-group
 * check, silently narrowing or breaking the {@code $or} semantics for that field.
 */
@AnalyzeClasses(
    packages = "io.camunda.search.filter",
    importOptions = DoNotIncludeTestsOrTestJars.class)
class OrFilterIsEmptyCoverageArchTest {

  // Every OrFilter implementation has orFilters as a structural component (the $or list itself,
  // not a criterion), so it's the default. Only additional structural components - join keys,
  // query hints - need to be listed here per class.
  static final Set<String> DEFAULT_STRUCTURAL_COMPONENTS = Set.of("orFilters");

  static final Map<String, Set<String>> STRUCTURAL_COMPONENTS =
      Map.of(
          "FlowNodeInstanceFilter",
          // useTreePathPrefix is a query-hint flag - whether treePath filtering should use a
          // prefix query while elementInstanceScopeKey is not yet universally populated
          // (pre-8.8 data) - not a user-supplied filter criterion.
          Set.of("orFilters", "useTreePathPrefix"),
          // processDefinitionKey is the mandatory join key shared by every $or group, not a
          // filter criterion.
          "ProcessDefinitionStatisticsFilter",
          Set.of("orFilters", "processDefinitionKey"));

  @ArchTest
  static final ArchRule EVERY_CRITERIA_COMPONENT_IS_CHECKED_BY_IS_EMPTY =
      ArchRuleDefinition.classes()
          .that(areNonInterfaceOrFilterImplementations())
          .should(referenceEveryCriteriaComponentInIsEmpty())
          .because(
              "a record component missing from isEmpty() would make that field invisible to the"
                  + " empty-$or-group check");

  private static DescribedPredicate<JavaClass> areNonInterfaceOrFilterImplementations() {
    return new DescribedPredicate<>("are non-interface implementations of OrFilter") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return !javaClass.isInterface() && javaClass.isAssignableTo(OrFilter.class);
      }
    };
  }

  private static ArchCondition<JavaClass> referenceEveryCriteriaComponentInIsEmpty() {
    return new ArchCondition<>("reference every criteria record component in isEmpty()") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {
        final var structuralComponents =
            STRUCTURAL_COMPONENTS.getOrDefault(
                javaClass.getSimpleName(), DEFAULT_STRUCTURAL_COMPONENTS);
        final var criteriaComponents =
            Arrays.stream(javaClass.reflect().getRecordComponents())
                .map(RecordComponent::getName)
                .filter(name -> !structuralComponents.contains(name))
                .collect(Collectors.toSet());

        final var fieldsReadByIsEmpty =
            javaClass.getMethod("isEmpty").getFieldAccesses().stream()
                .map(JavaFieldAccess::getTarget)
                .map(AccessTarget::getName)
                .collect(Collectors.toSet());

        final var missing =
            criteriaComponents.stream()
                .filter(name -> !fieldsReadByIsEmpty.contains(name))
                .collect(Collectors.toSet());

        if (missing.isEmpty()) {
          events.add(
              satisfied(javaClass, javaClass.getSimpleName() + "#isEmpty() covers every field"));
        } else {
          events.add(
              violated(
                  javaClass,
                  javaClass.getSimpleName()
                      + "#isEmpty() does not reference "
                      + missing
                      + "; wire it into the isEmpty() check, or add it to"
                      + " STRUCTURAL_COMPONENTS in OrFilterIsEmptyCoverageArchTest if it is not a"
                      + " filter criterion"));
        }
      }
    };
  }
}

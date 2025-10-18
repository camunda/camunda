/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.List;

/**
 * ArchUnit rules to enforce correct usage of the {@link io.camunda.zeebe.util.VisibleForTesting}
 * annotation.
 *
 * <p>Elements (classes, methods, fields) annotated with {@code @VisibleForTesting} must only be
 * accessed from test code or from within the same class (self-usage). This prevents accidental
 * usage in production code outside their intended scope.
 *
 * <p>Some classes are excluded from these rules via the {@code EXCLUDED_CLASS_NAMES} list. These
 * should be investigated and removed from the exclusion list over time.
 *
 * @see io.camunda.zeebe.util.VisibleForTesting
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public class VisibleForTestingArchTest {

  static final List<String> EXCLUDED_CLASS_NAMES =
      List.of(
          "io.camunda.zeebe.broker.system.partitions.impl.MigrationSnapshotDirector",
          "io.camunda.zeebe.gateway.impl.stream.StreamJobsHandler$AsyncJobStreamRemover",
          "io.camunda.zeebe.util.micrometer.StatefulGauge",
          "io.camunda.application.commons.console.ping.PingConsoleTask$RetriableException",
          "io.camunda.search.schema.IndexMappingDifference$OrderInsensitiveEquivalence",
          "io.camunda.zeebe.gateway.impl.stream.StreamJobsHandler$JobStreamConsumer",
          "io.camunda.zeebe.shared.management.ActorClockEndpoint$Response");

  @ArchTest
  static final ArchRule CLASS_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      classes()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should(classAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule METHOD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      methods()
          .that()
          .areAnnotatedWith(VisibleForTesting.class)
          .should(methodAccessedBySelfOrTest());

  @ArchTest
  static final ArchRule FIELD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_ACCESSED_FROM_TEST_OR_SELF =
      fields().that().areAnnotatedWith(VisibleForTesting.class).should(fieldAccessedBySelfOrTest());

  private static ArchCondition<JavaClass> classAccessedBySelfOrTest() {
    return new ArchCondition<>("Classes should be accessed from test or self") {
      @Override
      public void check(final JavaClass javaClass, final ConditionEvents events) {

        javaClass.getAccessesToSelf().stream()
            .filter(access -> isViolation(javaClass, access.getOriginOwner()))
            .forEach(
                violationAccess ->
                    events.add(
                        SimpleConditionEvent.violated(
                            violationAccess,
                            String.format(
                                "Class %s is accessed from %s, which is not a test or self class.",
                                javaClass.getFullName(),
                                violationAccess.getOriginOwner().getFullName()))));
      }
    };
  }

  private static ArchCondition<JavaMethod> methodAccessedBySelfOrTest() {
    return new ArchCondition<>("Methods should be accessed from test or self") {
      @Override
      public void check(final JavaMethod javaMethod, final ConditionEvents events) {

        javaMethod.getAccessesToSelf().stream()
            .filter(access -> isViolation(javaMethod.getOwner(), access.getOriginOwner()))
            .forEach(
                violationCall ->
                    events.add(
                        SimpleConditionEvent.violated(
                            violationCall,
                            String.format(
                                "Method %s is called from %s, which is not a test or self class.",
                                javaMethod.getFullName(),
                                violationCall.getOriginOwner().getFullName()))));
      }
    };
  }

  private static ArchCondition<JavaField> fieldAccessedBySelfOrTest() {
    return new ArchCondition<>("Fields should be accessed from test or self") {
      @Override
      public void check(final JavaField javaField, final ConditionEvents events) {

        javaField.getAccessesToSelf().stream()
            .filter(fieldAccess -> isViolation(javaField.getOwner(), fieldAccess.getOriginOwner()))
            .forEach(
                violationAccess ->
                    events.add(
                        SimpleConditionEvent.violated(
                            violationAccess,
                            String.format(
                                "Field %s is accessed from %s, which is not a test or self class.",
                                javaField.getFullName(),
                                violationAccess.getOriginOwner().getFullName()))));
      }
    };
  }

  private static boolean isViolation(final JavaClass javaOwner, final JavaClass javaOriginOwner) {
    return !isCalledBySelf(javaOwner, javaOriginOwner)
        && !isTestClass(javaOriginOwner)
        && !isExcludedClass(javaOwner);
  }

  private static boolean isTestClass(final JavaClass javaClass) {
    final var javaClassSource = javaClass.getSource();
    return javaClassSource.isPresent() && javaClassSource.get().toString().contains("test");
  }

  private static boolean isCalledBySelf(
      final JavaClass javaMethodOwner, final JavaClass javaMethodCallOriginOwner) {
    return javaMethodCallOriginOwner.equals(javaMethodOwner);
  }

  private static boolean isExcludedClass(final JavaClass javaClass) {
    return EXCLUDED_CLASS_NAMES.contains(javaClass.getFullName());
  }
}

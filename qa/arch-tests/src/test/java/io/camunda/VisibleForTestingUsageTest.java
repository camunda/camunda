/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.zeebe.util.VisibleForTesting;

@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public class VisibleForTestingUsageTest {

  @ArchTest
  public static final ArchRule METHOD_VISIBLE_FOR_TESTING_SHOULD_ONLY_BE_CALLED_FROM_TEST_OR_SELF =
      methods().that().areAnnotatedWith(VisibleForTesting.class).should(calledBySelfOrTest());

  private static ArchCondition<JavaMethod> calledBySelfOrTest() {
    return new ArchCondition<>("Methods should be called from test or self") {
      @Override
      public void check(final JavaMethod javaMethod, final ConditionEvents events) {

        javaMethod.getCallsOfSelf().stream()
            .filter(javaMethodCall -> isViolation(javaMethod.getOwner(), javaMethodCall))
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

  private static boolean isViolation(JavaClass javaMethodOwner, JavaMethodCall javaMethodCall) {
    final JavaClass javaMethodCallOriginOwner = javaMethodCall.getOriginOwner();
    return !isCalledBySelf(javaMethodOwner, javaMethodCallOriginOwner)
        && !isTestClass(javaMethodCallOriginOwner);
  }

  private static boolean isTestClass(final JavaClass javaClass) {
    final var javaClassSource = javaClass.getSource();
    return javaClassSource.isPresent() && javaClassSource.get().toString().contains("test");
  }

  private static boolean isCalledBySelf(
      final JavaClass javaMethodOwner, final JavaClass javaMethodCallOriginOwner) {
    return javaMethodCallOriginOwner.equals(javaMethodOwner);
  }
}

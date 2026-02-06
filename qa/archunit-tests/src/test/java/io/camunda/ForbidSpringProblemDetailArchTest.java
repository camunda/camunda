/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.http.ProblemDetail;

/**
 * This ArchUnit test ensures that {@link ProblemDetail} from Spring is never instantiated directly
 * in the codebase. Instead, {@link io.camunda.gateway.protocol.model.CamundaProblemDetail} should
 * be used.
 *
 * <p>Spring Framework 7 removed the default {@code about:blank} value for the {@code type} field in
 * {@link ProblemDetail} (see spring-projects/spring-framework#35294). {@link
 * io.camunda.gateway.protocol.model.CamundaProblemDetail} restores this default to ensure backward
 * compatibility with API consumers expecting the RFC 9457 compliant {@code about:blank} default
 * value.
 *
 * <p>This rule allows imports and type references to {@link ProblemDetail} (e.g., for method
 * signatures or type checks) but forbids creating instances via constructors or factory methods.
 */
@AnalyzeClasses(packages = "io.camunda", importOptions = ImportOption.DoNotIncludeTests.class)
public class ForbidSpringProblemDetailArchTest {

  private static final String CAMUNDA_PROBLEM_DETAIL_CLASS =
      "io.camunda.gateway.protocol.model.CamundaProblemDetail";

  private static final DescribedPredicate<JavaAccess<?>> PROBLEM_DETAIL_INSTANTIATION =
      new DescribedPredicate<>("instantiates or uses factory methods of ProblemDetail") {
        @Override
        public boolean test(final JavaAccess<?> access) {
          final JavaClass targetClass = access.getTargetOwner();
          if (!targetClass.isEquivalentTo(ProblemDetail.class)) {
            return false;
          }

          // Check for constructor calls
          if (access.getName().equals("<init>")) {
            return true;
          }

          // Check for static factory method calls (e.g., forStatus, forStatusAndDetail)
          if (access instanceof final JavaMethodCall methodCall) {
            final String methodName = methodCall.getName();
            return methodName.startsWith("forStatus") || "forStatusAndDetail".equals(methodName);
          }

          return false;
        }
      };

  private static final ArchCondition<JavaClass> NOT_INSTANTIATE_PROBLEM_DETAIL =
      new ArchCondition<>("not instantiate or use factory methods of ProblemDetail") {
        @Override
        public void check(final JavaClass javaClass, final ConditionEvents events) {
          javaClass.getAccessesFromSelf().stream()
              .filter(PROBLEM_DETAIL_INSTANTIATION)
              .forEach(
                  access ->
                      events.add(
                          SimpleConditionEvent.violated(
                              javaClass,
                              String.format(
                                  "%s instantiates or uses factory method of ProblemDetail at %s. "
                                      + "Use CamundaProblemDetail instead.",
                                  javaClass.getName(), access.getSourceCodeLocation()))));
        }
      };

  @ArchTest
  public static final ArchRule RULE_FORBID_PROBLEM_DETAIL_INSTANTIATION =
      noClasses()
          .that()
          .resideInAPackage("io.camunda..")
          .and()
          .doNotHaveFullyQualifiedName(CAMUNDA_PROBLEM_DETAIL_CLASS)
          .should(NOT_INSTANTIATE_PROBLEM_DETAIL)
          .as(
              "should use io.camunda.gateway.protocol.model.CamundaProblemDetail instead of "
                  + "instantiating org.springframework.http.ProblemDetail to ensure the 'type' "
                  + "field defaults to 'about:blank' as per RFC 9457");
}

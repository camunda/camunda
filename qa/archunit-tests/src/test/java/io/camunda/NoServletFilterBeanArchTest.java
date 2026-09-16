/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.AbstractFilterRegistrationBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot maps every {@link Filter}-typed {@link Bean} onto {@code /*}, so such a bean runs on
 * every single request whether or not that was intended. This has bitten the REST API twice: {@code
 * WebApplicationAuthorizationCheckFilter} (#35059) was explicitly registered in the security chains
 * and then silently added to all of them again, and the REST API composite filter ran on every
 * request even when no user-defined filters were configured (#35067).
 *
 * <p>A {@link Filter} bean is therefore only allowed alongside a {@link FilterRegistrationBean} in
 * the same configuration class. Such a registration decides where the filter runs — either by
 * naming URL patterns, or by {@code setEnabled(false)} to suppress the servlet mapping entirely for
 * a filter that is instead wired into specific security chains. Configurations that need no
 * injectable filter bean should skip the intermediate bean and expose only the registration.
 *
 * <p>The pairing is matched per declaring class, not per filter instance, so a configuration
 * declaring several filters is only checked to register at least one of them.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/35067">Issue #35067</a>
 */
@AnalyzeClasses(
    packages = "io.camunda",
    importOptions = {DoNotIncludeTestsOrTestJars.class})
public final class NoServletFilterBeanArchTest {

  private static final DescribedPredicate<JavaClass> SERVLET_FILTER =
      new DescribedPredicate<>("a servlet filter") {
        @Override
        public boolean test(final JavaClass javaClass) {
          return javaClass.isAssignableTo(Filter.class);
        }
      };

  private static final ArchCondition<JavaMethod> BE_PAIRED_WITH_A_FILTER_REGISTRATION =
      new ArchCondition<>("be paired with a FilterRegistrationBean in the same configuration") {
        @Override
        public void check(final JavaMethod method, final ConditionEvents events) {
          if (declaresFilterRegistrationBean(method.getOwner())) {
            return;
          }
          events.add(
              violated(
                  method,
                  String.format(
                      "%s declares the filter bean %s but no FilterRegistrationBean, so Spring Boot"
                          + " maps the filter onto /* and runs it on every request",
                      method.getOwner().getSimpleName(), method.getFullName())));
        }

        private boolean declaresFilterRegistrationBean(final JavaClass owner) {
          return owner.getMethods().stream()
              .filter(candidate -> candidate.isAnnotatedWith(Bean.class))
              .anyMatch(
                  candidate ->
                      candidate
                          .getRawReturnType()
                          .isAssignableTo(AbstractFilterRegistrationBean.class));
        }
      };

  @SuppressWarnings("unused")
  @ArchTest
  public static final ArchRule RULE_FILTER_BEANS_MUST_DECLARE_THEIR_REGISTRATION =
      methods()
          .that()
          .areAnnotatedWith(Bean.class)
          .and()
          .haveRawReturnType(SERVLET_FILTER)
          .should(BE_PAIRED_WITH_A_FILTER_REGISTRATION)
          .as("@Bean methods returning a servlet filter should declare their registration")
          .because(
              "an unregistered Filter bean is mapped onto /* by Spring Boot and runs on every "
                  + "request, including requests it was never meant to see");
}

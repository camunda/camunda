/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import io.camunda.archunit.DoNotIncludeSecurityLibrary;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.util.Optional;
import org.springframework.context.annotation.Bean;

/**
 * A {@code @Bean} method that returns a servlet filter must be accompanied, in the same
 * configuration class, by a {@code @Bean} method returning a {@code FilterRegistrationBean} for
 * that filter.
 *
 * <p>Spring Boot maps every {@code jakarta.servlet.Filter}-typed bean onto {@code /*}, so a filter
 * bean with no registration runs on every request whether or not that was intended. The
 * registration is where that decision gets written down: either it names the URL patterns the
 * filter should see, or it calls {@code setEnabled(false)} to suppress the servlet mapping for a
 * filter that is instead wired into specific security chains by hand. A bare {@code Filter} bean
 * states neither.
 *
 * <p>The registration is correlated by type: a {@code FilterRegistrationBean<T>} only satisfies a
 * filter bean whose return type is assignable to {@code T}. Matching merely on the presence of some
 * registration in the class is not enough, because a configuration may register one filter and
 * leave another auto-mapped — {@code CCSaaSSecurityConfigurerAdapter} in {@code optimize/backend}
 * has exactly that shape. A raw {@code FilterRegistrationBean} carries no type argument to
 * correlate against and so satisfies any filter bean in its class.
 *
 * <p><b>Scope.</b> Only code this repository can change. {@code camunda-security-library} shares
 * the {@code io.camunda} package root but ships as a released artifact, so it is excluded — a CSL
 * release that reshapes its own filter beans must not fail this build. {@code optimize-backend} is
 * not on this module's classpath either, so filter beans under {@code io.camunda.optimize} are not
 * checked; {@code CCSaaSSecurityConfigurerAdapter} there is a known instance of the shape this rule
 * forbids.
 *
 * <p>Within that scope no filter bean exists today, so the rule normally checks nothing. That is
 * the intended steady state for a guard, and is why it does not require a non-empty match. The two
 * rules below instead pin that the type names it is built on still resolve, which is what an empty
 * match would otherwise be hiding.
 */
@AnalyzeClasses(
    packages = "io.camunda",
    importOptions = {DoNotIncludeTestsOrTestJars.class, DoNotIncludeSecurityLibrary.class})
public final class NoServletFilterBeanArchTest {

  /**
   * Referenced by name rather than by class literal. Both {@code jakarta.servlet.Filter} and the
   * Spring Boot registration types reach this module only transitively, and importing them makes
   * {@code dependency:analyze} demand a declaration it then cannot attribute to a single artifact:
   * {@code jakarta.servlet-api} and {@code tomcat-embed-core} both supply the servlet API, so
   * declaring either one makes the analyzer blame the other. Losing the class literals also loses
   * the compile error an upstream rename would have caused, which is what {@link
   * #RULE_SERVLET_FILTER_TYPE_STILL_RESOLVES} and {@link
   * #RULE_FILTER_REGISTRATION_TYPE_STILL_RESOLVES} restore.
   */
  private static final String SERVLET_FILTER_TYPE = "jakarta.servlet.Filter";

  private static final String FILTER_REGISTRATION_TYPE =
      "org.springframework.boot.web.servlet.AbstractFilterRegistrationBean";

  private static final DescribedPredicate<JavaClass> SERVLET_FILTER =
      new DescribedPredicate<>("a servlet filter") {
        @Override
        public boolean test(final JavaClass javaClass) {
          return javaClass.isAssignableTo(SERVLET_FILTER_TYPE);
        }
      };

  private static final DescribedPredicate<JavaClass> FILTER_REGISTRATION =
      new DescribedPredicate<>("a filter registration") {
        @Override
        public boolean test(final JavaClass javaClass) {
          return javaClass.isAssignableTo(FILTER_REGISTRATION_TYPE);
        }
      };

  private static final ArchCondition<JavaMethod> BE_REGISTERED_BY_TYPE =
      new ArchCondition<>("be registered by a FilterRegistrationBean for the same filter type") {
        @Override
        public void check(final JavaMethod filterBean, final ConditionEvents events) {
          if (isRegisteredIn(filterBean.getOwner(), filterBean.getRawReturnType())) {
            return;
          }
          events.add(
              violated(
                  filterBean,
                  String.format(
                      "%s declares the filter bean %s with no FilterRegistrationBean<%s>, so Spring"
                          + " Boot maps the filter onto /* and runs it on every request",
                      filterBean.getOwner().getSimpleName(),
                      filterBean.getFullName(),
                      filterBean.getRawReturnType().getSimpleName())));
        }

        /**
         * A registration satisfies the filter bean when its type argument is a supertype of (or the
         * same as) the filter's type — {@code FilterRegistrationBean<Filter>} covers a {@code
         * SessionRepositoryFilter} bean, while {@code FilterRegistrationBean<OtherFilter>} does
         * not. A raw registration has no type argument and satisfies anything.
         */
        private boolean isRegisteredIn(final JavaClass owner, final JavaClass filterType) {
          return owner.getMethods().stream()
              .filter(candidate -> candidate.isAnnotatedWith(Bean.class))
              .filter(candidate -> FILTER_REGISTRATION.test(candidate.getRawReturnType()))
              .anyMatch(
                  registration ->
                      registeredFilterType(registration)
                          .map(registered -> filterType.isAssignableTo(registered.getName()))
                          .orElse(true));
        }

        /** The {@code T} of a {@code FilterRegistrationBean<T>} return type, if declared. */
        private Optional<JavaClass> registeredFilterType(final JavaMethod registration) {
          final JavaType returnType = registration.getReturnType();
          if (!(returnType instanceof final JavaParameterizedType parameterized)) {
            return Optional.empty();
          }
          return parameterized.getActualTypeArguments().stream()
              .findFirst()
              .flatMap(NoServletFilterBeanArchTest::toRawClass);
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
          .should(BE_REGISTERED_BY_TYPE)
          // No filter bean exists in scope today; this rule guards against the next one.
          .allowEmptyShould(true)
          .as("@Bean methods returning a servlet filter should declare their registration")
          .because(
              "an unregistered Filter bean is mapped onto /* by Spring Boot and runs on every "
                  + "request, including requests it was never meant to see");

  /**
   * Both anchor types are named as strings, so a rename or package move upstream would quietly stop
   * {@link #SERVLET_FILTER} matching anything and leave the rule above permanently satisfied. These
   * two rules fail instead: each selects by the same name the rule depends on and requires a
   * non-empty match, so the build breaks with "failed to check any classes" rather than passing
   * vacuously. They assert nothing about the classes they find — resolving at all is the point.
   */
  private static final ArchCondition<Object> RESOLVE =
      new ArchCondition<>("resolve to at least one class") {
        @Override
        public void check(final Object item, final ConditionEvents events) {
          // Intentionally empty: selection is the assertion.
        }
      };

  @SuppressWarnings("unused")
  @ArchTest
  public static final ArchRule RULE_SERVLET_FILTER_TYPE_STILL_RESOLVES =
      classes()
          .that(SERVLET_FILTER)
          .should(RESOLVE)
          .allowEmptyShould(false)
          .as("the servlet filter type name used by this rule should still resolve")
          .because(
              SERVLET_FILTER_TYPE
                  + " is referenced by name; if it moves, the rule above silently stops matching");

  @SuppressWarnings("unused")
  @ArchTest
  public static final ArchRule RULE_FILTER_REGISTRATION_TYPE_STILL_RESOLVES =
      methods()
          .that()
          .areAnnotatedWith(Bean.class)
          .and()
          .haveRawReturnType(FILTER_REGISTRATION)
          .should(RESOLVE)
          .allowEmptyShould(false)
          .as("the filter registration type name used by this rule should still resolve")
          .because(
              FILTER_REGISTRATION_TYPE
                  + " is referenced by name; if it moves, every filter bean looks unregistered");

  /**
   * Wildcards ({@code FilterRegistrationBean<?>}) and type variables carry no correlatable class,
   * and are treated like a raw registration.
   */
  private static Optional<JavaClass> toRawClass(final JavaType type) {
    if (type instanceof final JavaClass javaClass) {
      return Optional.of(javaClass);
    }
    if (type instanceof final JavaParameterizedType parameterized) {
      return Optional.of(parameterized.toErasure());
    }
    return Optional.empty();
  }
}

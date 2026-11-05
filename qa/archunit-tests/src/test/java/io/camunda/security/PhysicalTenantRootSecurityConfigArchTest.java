/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import io.camunda.archunit.DoNotIncludeSecurityLibrary;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.util.Map;

/**
 * Code that serves a request of one physical tenant must not read the root security configuration.
 *
 * <p>{@code CamundaSecurityLibraryProperties} is the cluster's security configuration. With
 * physical tenants, it is the {@code default} tenant's configuration. A class that serves a request
 * of one tenant and reads it gets the {@code default} tenant's values for every tenant
 * (camunda/camunda#68205, #68206, #68209). Such a class must read the tenant's own configuration
 * instead.
 *
 * <p>A class serves a tenant's request when it uses {@code PhysicalTenantContext} or has a
 * {@code @PhysicalTenantId} parameter. The check works per class: a nested class counts on its own,
 * and is listed by its outer class's name.
 *
 * <p>{@link #EXCEPTIONS} lists the classes that the rule flagged when it was added. Each entry
 * gives its reason or the issue that removes it. A class may read a property that can't be
 * overridden per tenant ({@code PhysicalTenantOverridePolicyValidation} denies it), such as {@code
 * authentication.method}; that is a permanent exception. Remove an entry when its fix merges:
 * {@link #exceptionsMustStillBeNeeded} fails when an entry no longer matches a violation.
 *
 * <p>The three types are referenced by name, because CSL's starter is not a dependency of this
 * module. The {@code *_NAME_STILL_RESOLVES} rules fail if a name stops resolving, so that a rename
 * can't leave the main rule silently empty.
 */
@AnalyzeClasses(
    packages = "io.camunda",
    importOptions = {DoNotIncludeTestsOrTestJars.class, DoNotIncludeSecurityLibrary.class})
public final class PhysicalTenantRootSecurityConfigArchTest {

  private static final String ROOT_SECURITY_CONFIG =
      "io.camunda.security.spring.CamundaSecurityLibraryProperties";
  private static final String PHYSICAL_TENANT_CONTEXT =
      "io.camunda.spring.utils.PhysicalTenantContext";
  private static final String PHYSICAL_TENANT_ID =
      "io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId";

  /** Top-level class name to the reason it may read the root configuration, or its issue. */
  private static final Map<String, String> EXCEPTIONS =
      Map.of(
          "io.camunda.zeebe.gateway.rest.controller.authentication.AuthenticationController",
          "reads unprotected-api, which can't be overridden per tenant; uses the root"
              + " authorizations flag only when no WebAppAuthorizationEnabledPort exists",
          "io.camunda.zeebe.gateway.rest.controller.setup.SetupController",
          "reads authentication.method, which can't be overridden per tenant",
          "io.camunda.zeebe.gateway.rest.controller.system.SystemController",
          "reads multi-tenancy and saas, which can't be overridden per tenant; passes the root"
              + " authentication to PhysicalTenantCamundaGroups as the default tenant's",
          "io.camunda.identity.webapp.controllers.AdminClientConfigController",
          "reads authentication.method, multi-tenancy and saas, which can't be overridden per"
              + " tenant; uses the root as the default tenant's configuration for the groups"
              + " mode, the additional IdP and the ID pattern",
          "io.camunda.authentication.service.PhysicalTenantGroupsClaims",
          "uses the root only as the default tenant's configuration",
          "io.camunda.authentication.service.DefaultMembershipService",
          "passes the root to PhysicalTenantGroupsClaims as the default tenant's configuration",
          "io.camunda.application.commons.security.WebAppAuthorizationCheckPortConfiguration",
          "reads multi-tenancy, which can't be overridden per tenant; uses the root authorizations"
              + " flag and OIDC claims only for its fallbacks without per-tenant beans");

  private static final DescribedPredicate<JavaClass> SERVES_A_PHYSICAL_TENANT_REQUEST =
      new DescribedPredicate<>("serve a request of one physical tenant") {
        @Override
        public boolean test(final JavaClass javaClass) {
          return dependsOn(javaClass, PHYSICAL_TENANT_CONTEXT)
              || javaClass.getCodeUnits().stream()
                  .anyMatch(PhysicalTenantRootSecurityConfigArchTest::hasPhysicalTenantIdParameter);
        }
      };

  private static final ArchCondition<JavaClass> NOT_READ_THE_ROOT_SECURITY_CONFIG =
      new ArchCondition<>("not read the root security configuration") {
        @Override
        public void check(final JavaClass javaClass, final ConditionEvents events) {
          if (dependsOn(javaClass, ROOT_SECURITY_CONFIG)
              && !EXCEPTIONS.containsKey(topLevelName(javaClass))) {
            events.add(
                violated(
                    javaClass,
                    String.format(
                        "%s serves a request of one physical tenant and reads"
                            + " CamundaSecurityLibraryProperties, which is the default tenant's"
                            + " configuration. Read the request tenant's configuration instead.",
                        javaClass.getName())));
          }
        }
      };

  @ArchTest
  static final ArchRule PER_TENANT_CODE_MUST_NOT_READ_THE_ROOT_SECURITY_CONFIG =
      classes()
          .that(SERVES_A_PHYSICAL_TENANT_REQUEST)
          .should(NOT_READ_THE_ROOT_SECURITY_CONFIG)
          .because("the root security configuration is the default physical tenant's");

  @ArchTest
  static final ArchRule ROOT_SECURITY_CONFIG_NAME_STILL_RESOLVES =
      typeNameStillResolves(ROOT_SECURITY_CONFIG);

  @ArchTest
  static final ArchRule PHYSICAL_TENANT_CONTEXT_NAME_STILL_RESOLVES =
      typeNameStillResolves(PHYSICAL_TENANT_CONTEXT);

  @ArchTest
  static final ArchRule PHYSICAL_TENANT_ID_NAME_STILL_RESOLVES =
      classes()
          .that(
              new DescribedPredicate<>("have a @PhysicalTenantId parameter") {
                @Override
                public boolean test(final JavaClass javaClass) {
                  return javaClass.getCodeUnits().stream()
                      .anyMatch(
                          PhysicalTenantRootSecurityConfigArchTest::hasPhysicalTenantIdParameter);
                }
              })
          .should(resolve())
          .allowEmptyShould(false)
          .as(PHYSICAL_TENANT_ID + " should still be used")
          .because("the rule above finds per-tenant controllers by this name");

  private PhysicalTenantRootSecurityConfigArchTest() {}

  /** An exception whose class no longer violates the rule must be removed. */
  @ArchTest
  static void exceptionsMustStillBeNeeded(final JavaClasses classes) {
    for (final var exception : EXCEPTIONS.keySet()) {
      assertThat(
              classes.stream()
                  .filter(javaClass -> topLevelName(javaClass).equals(exception))
                  .anyMatch(
                      javaClass ->
                          SERVES_A_PHYSICAL_TENANT_REQUEST.test(javaClass)
                              && dependsOn(javaClass, ROOT_SECURITY_CONFIG)))
          .as(
              "%s is an exception of the rule but no longer reads the root security"
                  + " configuration in per-tenant code; remove it from EXCEPTIONS",
              exception)
          .isTrue();
    }
  }

  private static ArchRule typeNameStillResolves(final String typeName) {
    return classes()
        .that(
            new DescribedPredicate<>("depend on " + typeName) {
              @Override
              public boolean test(final JavaClass javaClass) {
                return dependsOn(javaClass, typeName);
              }
            })
        .should(resolve())
        .allowEmptyShould(false)
        .as(typeName + " should still be used")
        .because("the rule above refers to it by name");
  }

  /** Selection is the assertion: the rule fails when nothing is selected. */
  private static ArchCondition<JavaClass> resolve() {
    return new ArchCondition<>("exist") {
      @Override
      public void check(final JavaClass item, final ConditionEvents events) {}
    };
  }

  private static boolean dependsOn(final JavaClass javaClass, final String typeName) {
    return javaClass.getDirectDependenciesFromSelf().stream()
        .anyMatch(dependency -> dependency.getTargetClass().getName().equals(typeName));
  }

  private static boolean hasPhysicalTenantIdParameter(final JavaCodeUnit codeUnit) {
    return codeUnit.getParameters().stream()
        .anyMatch(parameter -> parameter.isAnnotatedWith(PHYSICAL_TENANT_ID));
  }

  private static String topLevelName(final JavaClass javaClass) {
    final String name = javaClass.getName();
    final int nested = name.indexOf('$');
    return nested < 0 ? name : name.substring(0, nested);
  }
}

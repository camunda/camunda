/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static com.tngtech.archunit.lang.SimpleConditionEvent.satisfied;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * Enforces that every public method in {@code io.camunda.service.*Services} classes declares a
 * {@code String physicalTenantId} parameter, ensuring that tenant-scoped operations can be routed
 * to the correct physical data store.
 *
 * <p>Parameter names are read via Java reflection. The project is compiled with {@code -parameters}
 * (see {@code <parameters>true</parameters>} in parent/pom.xml), so reflection returns the real
 * parameter names rather than {@code arg0}, {@code arg1}, etc.
 */
@AnalyzeClasses(
    packages = "io.camunda.service",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ServicePhysicalTenantIdArchTest {

  @ArchTest
  static final ArchRule SERVICES_PUBLIC_METHODS_MUST_ACCEPT_PHYSICAL_TENANT_ID =
      methods()
          .that()
          .arePublic()
          .and()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("Services")
          .and()
          .areDeclaredInClassesThat()
          .resideInAPackage("io.camunda.service")
          .and()
          .areDeclaredInClassesThat(areNotAbstractClasses())
          .and()
          .areDeclaredInClassesThat(areNotInnerClasses())
          .and()
          .areDeclaredInClassesThat(areNotExcludedClasses())
          .and(areNotExcludedMethods())
          .should(havePhysicalTenantIdParameter())
          .because(
              "All *Services methods must declare 'String physicalTenantId' to route"
                  + " requests to the correct physical index or store in a multi-tenant setup");

  /** Parameter name all compliant service methods must include. */
  static final String PHYSICAL_TENANT_ID = "physicalTenantId";

  /**
   * Services excluded by design — these don't participate in tenant-scoped operations and
   * intentionally omit {@code physicalTenantId} from their public API.
   */
  static final Set<String> EXCLUDED_CLASSES =
      Set.of(
          // License management — no authentication or tenant context needed
          ManagementServices.class.getSimpleName(),
          // Cluster topology and health checks — infrastructure-level, not tenant-scoped
          TopologyServices.class.getSimpleName());

  /**
   * Individual methods excluded from specific classes.
   *
   * <p>Use this for one-off methods that intentionally omit {@code physicalTenantId}, or as a
   * stepping-stone during migration when only some methods in a class still need updating.
   *
   * <p>Format: {@code "ClassName" -> Set.of("methodName1", "methodName2")}
   */
  static final Map<String, Set<String>> EXCLUDED_METHODS = Map.of();

  private static DescribedPredicate<JavaClass> areNotAbstractClasses() {
    return new DescribedPredicate<>("are not abstract") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return !javaClass.getModifiers().contains(JavaModifier.ABSTRACT);
      }
    };
  }

  private static DescribedPredicate<JavaClass> areNotInnerClasses() {
    return new DescribedPredicate<>("are not inner or nested classes") {
      @Override
      public boolean test(final JavaClass javaClass) {
        return javaClass.getEnclosingClass().isEmpty();
      }
    };
  }

  private static DescribedPredicate<JavaClass> areNotExcludedClasses() {
    return new DescribedPredicate<>("are not excluded from physicalTenantId enforcement") {
      @Override
      public boolean test(final JavaClass javaClass) {
        final String name = javaClass.getSimpleName();
        return !EXCLUDED_CLASSES.contains(name);
      }
    };
  }

  private static DescribedPredicate<JavaMethod> areNotExcludedMethods() {
    return new DescribedPredicate<>("are not individually excluded from physicalTenantId check") {
      @Override
      public boolean test(final JavaMethod method) {
        final Set<String> excluded = EXCLUDED_METHODS.get(method.getOwner().getSimpleName());
        return excluded == null || !excluded.contains(method.getName());
      }
    };
  }

  private static ArchCondition<JavaMethod> havePhysicalTenantIdParameter() {
    return new ArchCondition<>("have a 'String physicalTenantId' parameter") {
      @Override
      public void check(final JavaMethod method, final ConditionEvents events) {
        final java.lang.reflect.Method reflected;
        try {
          reflected = method.reflect();
        } catch (final Exception ignored) {
          // Compiler-generated (bridge/synthetic) methods may not be reflectable; skip them.
          return;
        }

        // Bridge methods (covariant overrides for generics) and synthetic methods don't need
        // physicalTenantId — skip them to avoid false violations.
        if (reflected.isBridge() || reflected.isSynthetic()) {
          return;
        }

        final boolean hasParam =
            Arrays.stream(reflected.getParameters())
                .anyMatch(
                    p -> p.getType() == String.class && PHYSICAL_TENANT_ID.equals(p.getName()));

        if (hasParam) {
          events.add(
              satisfied(
                  method,
                  String.format(
                      "%s#%s correctly declares 'String physicalTenantId'",
                      method.getOwner().getSimpleName(), method.getName())));
        } else {
          events.add(
              violated(
                  method,
                  String.format(
                      "%s#%s is missing a 'String physicalTenantId' parameter",
                      method.getOwner().getSimpleName(), method.getName())));
        }
      }
    };
  }
}

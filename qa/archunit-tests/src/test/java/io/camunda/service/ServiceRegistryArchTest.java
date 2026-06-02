/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import io.camunda.service.registry.ServiceRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** ArchUnit rules for the {@link ServiceRegistry} contract. */
@AnalyzeClasses(packages = "io.camunda.service", importOptions = DoNotIncludeTestsOrTestJars.class)
public final class ServiceRegistryArchTest {

  /**
   * R1 — every non-abstract service in {@code io.camunda.service} must be declared in {@link
   * ServiceRegistry}.
   *
   * <p>Candidates are all concrete (non-abstract) classes whose simple name ends with {@code
   * Services}. Each candidate must be the return type of at least one method on {@link
   * ServiceRegistry}.
   */
  @ArchTest
  public static final ArchRule R1_EVERY_SERVICE_MUST_BE_DECLARED_IN_SERVICE_REGISTRY =
      ArchRuleDefinition.classes()
          .that()
          .haveFullyQualifiedName(ServiceRegistry.class.getName())
          .should(containAllNonAbstractServices())
          .because(
              "every service must be accessible via ServiceRegistry so that"
                  + " controllers never instantiate or inject per-tenant services directly");

  /**
   * R2 — every tenant scoped service on {@link ServiceRegistry} that returns a tenant-scoped
   * service must accept a single {@code String physicalTenantId} parameter.
   *
   * <p>A method is considered a tenant-scoped accessor when its return type is a non-abstract class
   * in {@code io.camunda.service} whose name ends with {@code Services} and that is not listed in
   * {@link #CLUSTER_WIDE_SERVICES}.
   */
  @ArchTest
  public static final ArchRule R2_TENANT_SCOPED_ACCESSORS_MUST_ACCEPT_PHYSICAL_TENANT_ID =
      ArchRuleDefinition.classes()
          .that()
          .haveFullyQualifiedName(ServiceRegistry.class.getName())
          .should(haveSingleStringParamOnAllTenantScopedAccessors())
          .because("tenant-scoped service accessors must be keyed by physicalTenantId");

  static final Set<String> CLUSTER_WIDE_SERVICES = Set.of(ManagementServices.class.getName());

  // -- conditions --

  private static ArchCondition<JavaClass> containAllNonAbstractServices() {
    return new ArchCondition<>(
        "have an accessor method for every non-abstract *Services class in io.camunda.service") {

      private List<JavaClass> tenantScopedServices;

      @Override
      public void init(final Collection<JavaClass> allClasses) {
        tenantScopedServices = findServiceCandidates(allClasses);
      }

      @Override
      public void check(final JavaClass serviceRegistry, final ConditionEvents events) {
        final Set<String> returnTypeNames =
            serviceRegistry.getMethods().stream()
                .map(m -> m.getRawReturnType().getName())
                .collect(Collectors.toSet());

        for (final JavaClass service : tenantScopedServices) {
          if (!returnTypeNames.contains(service.getName())) {
            events.add(
                violated(
                    serviceRegistry,
                    String.format(
                        "Service '%s' is not registered in ServiceRegistry — add a method to ServiceRegistry and a"
                            + " corresponding method to DefaultServiceRegistry",
                        service.getSimpleName())));
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> haveSingleStringParamOnAllTenantScopedAccessors() {
    return new ArchCondition<>(
        "have a single String physicalTenantId parameter on each tenant-scoped service accessor") {

      private Set<String> tenantScopedServiceNames;

      @Override
      public void init(final Collection<JavaClass> allClasses) {
        tenantScopedServiceNames =
            findTenantScopedServiceCandidates(allClasses).stream()
                .map(JavaClass::getName)
                .collect(Collectors.toSet());
      }

      @Override
      public void check(final JavaClass serviceRegistry, final ConditionEvents events) {
        for (final JavaMethod method : serviceRegistry.getMethods()) {
          final String returnTypeName = method.getRawReturnType().getName();
          if (!tenantScopedServiceNames.contains(returnTypeName)) {
            continue;
          }
          final boolean hasSingleStringParam =
              method.getRawParameterTypes().size() == 1
                  && method.getRawParameterTypes().getFirst().isEquivalentTo(String.class);
          if (!hasSingleStringParam) {
            events.add(
                violated(
                    serviceRegistry,
                    String.format(
                        "ServiceRegistry method '%s' returns tenant-scoped service '%s' but does"
                            + " not accept a single String physicalTenantId parameter",
                        method.getName(), method.getRawReturnType().getSimpleName())));
          }
        }
      }
    };
  }

  // -- helpers --

  private static List<JavaClass> findServiceCandidates(final Collection<JavaClass> allClasses) {
    return allClasses.stream()
        .filter(c -> c.getPackageName().equals("io.camunda.service"))
        .filter(c -> c.getSimpleName().endsWith("Services"))
        .filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
        .toList();
  }

  private static List<JavaClass> findTenantScopedServiceCandidates(
      final Collection<JavaClass> allClasses) {
    return allClasses.stream()
        .filter(c -> c.getPackageName().equals("io.camunda.service"))
        .filter(c -> c.getSimpleName().endsWith("Services"))
        .filter(c -> !c.getModifiers().contains(JavaModifier.ABSTRACT))
        .filter(c -> !CLUSTER_WIDE_SERVICES.contains(c.getName()))
        .toList();
  }
}

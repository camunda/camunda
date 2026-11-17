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

import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.DoNotIncludeTestsOrTestJars;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ArchUnit rules to ensure the service layer uses security context appropriately when calling
 * search clients.
 *
 * <p>This test validates that service methods calling search client methods properly set security
 * context via {@code withSecurityContext} before accessing data.
 */
@AnalyzeClasses(packages = "io.camunda.service", importOptions = DoNotIncludeTestsOrTestJars.class)
public final class ServiceSecurityContextArchTest {

  /**
   * R1 — Methods in service classes that call search client methods must call {@code
   * withSecurityContext} before calling data-fetching methods.
   *
   * <p>This rule ensures that whenever a service method calls a search client method (like {@code
   * searchUsers}, {@code getUser}, {@code searchGroups}, etc.), it must first call {@code
   * withSecurityContext} to provide proper authorization.
   *
   * <p>Exception: Methods in {@code ProcessDefinitionProvider} are exempt because it sets the
   * security context once in the constructor.
   */
  @ArchTest
  public static final ArchRule
      R1_SERVICE_METHODS_MUST_USE_WITH_SECURITY_CONTEXT_BEFORE_SEARCH_CLIENT_CALLS =
          ArchRuleDefinition.methods()
              .that()
              .areDeclaredInClassesThat()
              .resideInAPackage("io.camunda.service..")
              .should(useSecurityContextBeforeSearchClientCalls())
              .because(
                  "search client methods must be called with proper security context to ensure"
                      + " authorization checks are performed");

  /**
   * R2 — {@code ProcessDefinitionProvider} constructor must call {@code withSecurityContext} on the
   * search client.
   *
   * <p>R1 exempts {@code ProcessDefinitionProvider} only because it sets the security context once
   * in the constructor. This rule pins that assumption so removing the constructor call does not
   * silently invalidate the exemption.
   */
  @ArchTest
  public static final ArchRule
      R2_PROCESS_DEFINITION_PROVIDER_CONSTRUCTOR_MUST_SET_SECURITY_CONTEXT =
          ArchRuleDefinition.constructors()
              .that()
              .areDeclaredInClassesThat()
              .haveFullyQualifiedName("io.camunda.service.cache.ProcessDefinitionProvider")
              .should(callWithSecurityContextInConstructor())
              .because(
                  "ProcessDefinitionProvider is exempt from R1 only because its constructor calls"
                      + " withSecurityContext — removing that call would bypass security checks"
                      + " silently");

  private static ArchCondition<JavaMethod> useSecurityContextBeforeSearchClientCalls() {
    return new ArchCondition<JavaMethod>(
        "call withSecurityContext before calling search client data-fetching methods") {
      @Override
      public void check(final JavaMethod method, final ConditionEvents events) {

        // Exempt ProcessDefinitionProvider - it sets security context in constructor
        if (method
            .getOwner()
            .getName()
            .equals("io.camunda.service.cache.ProcessDefinitionProvider")) {
          events.add(
              satisfied(
                  method,
                  String.format(
                      "Method %s.%s is exempt (ProcessDefinitionProvider sets security context in"
                          + " constructor)",
                      method.getOwner().getSimpleName(), method.getName())));
          return;
        }

        final Set<JavaMethodCall> methodCalls = method.getMethodCallsFromSelf();

        // Find all calls to search client data-fetching methods
        final Set<JavaMethodCall> searchClientDataCalls =
            methodCalls.stream()
                .filter(ServiceSecurityContextArchTest::isSearchClientDataMethod)
                .collect(Collectors.toSet());

        if (searchClientDataCalls.isEmpty()) {
          // No search client calls, so this method is fine
          events.add(
              satisfied(
                  method,
                  String.format(
                      "Method %s.%s does not call any search client data-fetching methods",
                      method.getOwner().getSimpleName(), method.getName())));
          return;
        }

        // Check if withSecurityContext is called before the search client data calls
        final boolean hasWithSecurityContextCall =
            methodCalls.stream()
                .anyMatch(ServiceSecurityContextArchTest::isWithSecurityContextCall);

        if (!hasWithSecurityContextCall) {
          final String dataMethodNames =
              searchClientDataCalls.stream()
                  .map(call -> call.getTarget().getName())
                  .distinct()
                  .collect(Collectors.joining(", "));

          events.add(
              violated(
                  method,
                  String.format(
                      "Method %s.%s calls search client data-fetching method(s) [%s] but does not"
                          + " call withSecurityContext to set security context",
                      method.getOwner().getSimpleName(), method.getName(), dataMethodNames)));
        } else {
          events.add(
              satisfied(
                  method,
                  String.format(
                      "Method %s.%s properly calls withSecurityContext before search client calls",
                      method.getOwner().getSimpleName(), method.getName())));
        }
      }
    };
  }

  /**
   * Checks if a method call is to a search client data-fetching method.
   *
   * <p>Search client data-fetching methods are those that retrieve data (e.g., search*, get*,
   * find*) but not the withSecurityContext method itself.
   */
  private static boolean isSearchClientDataMethod(final JavaMethodCall call) {
    final String targetClassName = call.getTarget().getOwner().getName();
    final String methodName = call.getTarget().getName();

    // Check if it's a search client interface
    final boolean isSearchClient = targetClassName.endsWith("SearchClient");

    if (!isSearchClient) {
      return false;
    }

    // Exclude withSecurityContext itself
    if ("withSecurityContext".equals(methodName)) {
      return false;
    }

    // Include common data-fetching get/set and unique ones to be refactored
    return methodName.startsWith("search")
        || methodName.startsWith("get")
        || "incidentProcessInstanceStatisticsByError".equals(methodName)
        || methodName.startsWith("processDefinition")
        || "processInstanceFlowNodeStatistics".equals(methodName)
        || methodName.startsWith("usageMetric");
  }

  /** Checks if a method call is to the withSecurityContext method. */
  private static boolean isWithSecurityContextCall(final JavaMethodCall call) {
    final String targetClassName = call.getTarget().getOwner().getName();
    final String methodName = call.getTarget().getName();

    return targetClassName.endsWith("SearchClient") && "withSecurityContext".equals(methodName);
  }

  private static ArchCondition<JavaConstructor> callWithSecurityContextInConstructor() {
    return new ArchCondition<>("call withSecurityContext on a SearchClient in the constructor") {
      @Override
      public void check(final JavaConstructor constructor, final ConditionEvents events) {
        final boolean callsWithSecurityContext =
            constructor.getMethodCallsFromSelf().stream()
                .anyMatch(ServiceSecurityContextArchTest::isWithSecurityContextCall);

        if (callsWithSecurityContext) {
          events.add(
              satisfied(
                  constructor,
                  "ProcessDefinitionProvider constructor calls withSecurityContext on the search"
                      + " client"));
        } else {
          events.add(
              violated(
                  constructor,
                  "ProcessDefinitionProvider constructor does not call withSecurityContext on the"
                      + " search client — the R1 exemption is no longer valid"));
        }
      }
    };
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;

import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.client.api.command.CommandWithTenantStep;
import io.camunda.client.impl.command.CreateProcessInstanceWithResultCommandImpl;
import java.util.Set;

@AnalyzeClasses(
    packages = "io.camunda.client",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class DefaultTenantAwarenessArchTest {

  /**
   * Tries to ensure that every command implementation that is tenant-aware respects the configured
   * default tenant.
   *
   * @implNote Every class implementing {@link CommandWithTenantStep} must have a constructor that
   *     retrieves the default tenant via {@link CamundaClientConfiguration#getDefaultTenantId()}.
   *     Doesn't check how the default tenant is used, we assume that no one retrieves it but then
   *     uses it for anything other than the default tenant for this command.
   */
  @ArchTest
  static final ArchRule COMMAND_WITH_TENANT_STEP_MUST_INITIALIZE_DEFAULT_TENANT =
      constructors()
          .that()
          .areDeclaredInClassesThat()
          .areAssignableTo(CommandWithTenantStep.class)
          .and()
          // Some constructors are only there for backwards compatibility and we can't change them
          // use the default tenant.
          .areNotAnnotatedWith(Deprecated.class)
          .and()
          // Special case: This command inherits the default tenant id from the
          // CreateProcessInstanceCommandImpl that it wraps.
          .areNotDeclaredIn(CreateProcessInstanceWithResultCommandImpl.class)
          .should(useDefaultTenant());

  private static ArchCondition<JavaConstructor> useDefaultTenant() {
    return new ArchCondition<JavaConstructor>(
        "have a constructor that retrieves the default tenant from `CamundaClientConfiguration`") {
      @Override
      public void check(final JavaConstructor constructor, final ConditionEvents events) {
        final Class<?> commandClass = constructor.getOwner().reflect();
        final Set<JavaMethodCall> allCallsFromConstructor = constructor.getMethodCallsFromSelf();

        final boolean callsGetDefault =
            allCallsFromConstructor.stream()
                .anyMatch(DefaultTenantAwarenessArchTest::isGetDefaultTenantIdCall);
        if (!callsGetDefault) {
          events.add(
              SimpleConditionEvent.violated(
                  constructor,
                  String.format(
                      "Command %s is tenant-aware but doesn't use the default tenant from %s",
                      commandClass.getName(), CamundaClientConfiguration.class.getName())));
        } else {
          events.add(
              SimpleConditionEvent.satisfied(
                  constructor,
                  String.format(
                      "Command %s retrieves the default tenant from %s",
                      commandClass.getName(), CamundaClientConfiguration.class.getName())));
        }
      }
    };
  }

  private static boolean isGetDefaultTenantIdCall(final JavaMethodCall call) {
    return call.getTargetOwner().isEquivalentTo(CamundaClientConfiguration.class)
        && (call.getName().equals("getDefaultTenantId")
            || call.getName().equals("getDefaultJobWorkerTenantIds"));
  }
}

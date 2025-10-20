/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.camunda.service.ApiServices;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import java.util.Comparator;

@AnalyzeClasses(
    packages = "io.camunda.zeebe.gateway.rest.controller..",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class ControllerAuthorizationArchTest {
  public static final String WITH_AUTHENTICATION_METHOD_NAME = "withAuthentication";

  @ArchTest()
  public static final ArchRule CONTROLLERS_CHECK_AUTHORIZATION =
      fields()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(CamundaRestController.class)
          .and(areAssignableToApiServices())
          .should(addAuthenticationToServiceCalls());

  /**
   * Predicate to check if the field is a Service class. We do this by verifying if it (indirectly)
   * extends {@link ApiServices}.
   */
  private static DescribedPredicate<JavaField> areAssignableToApiServices() {
    return new DescribedPredicate<>("are assignable to ApiServices") {
      @Override
      public boolean test(final JavaField javaField) {
        // Only check this field if it is a Service class (it (indirectly) extends ApiServices).
        final var serviceClass = javaField.getRawType();
        return serviceClass.isAssignableTo(ApiServices.class);
      }
    };
  }

  /**
   * ArchCondition to verify if the first call to the service field is {@link
   * ApiServices#withAuthentication}. If it isn't we will add a violation, resulting in a failed
   * test case.
   */
  private static ArchCondition<JavaField> addAuthenticationToServiceCalls() {
    return new ArchCondition<>("add authentication to the service call") {
      @Override
      public void check(final JavaField serviceField, final ConditionEvents events) {
        final var clazz = serviceField.getOwner();
        // We get all the methods in the class that make any call to the service field. After this
        // we ensure that the first call made to the service field is the `withAuthentication`
        // method.
        clazz.getMethods().stream()
            .filter(method -> callsService(method, serviceField))
            .filter(method -> !callsWithAuthenticationFirst(method, serviceField))
            .forEach(
                method ->
                    events.add(
                        SimpleConditionEvent.violated(
                            serviceField,
                            "%s is accessed without first calling withAuthentication in class %s in method %s at: %s"
                                .formatted(
                                    serviceField.getName(),
                                    clazz.getName(),
                                    method.getName(),
                                    method.getSourceCodeLocation()))));
      }
    };
  }

  /**
   * Verifies if the method actually calls the service field we are working with.
   *
   * @param method The method to check
   * @param serviceField The service field that should be called
   * @return true if the method makes a call to the service field, false otherwise
   */
  private static boolean callsService(final JavaMethod method, final JavaField serviceField) {
    final var callsFromSelf = method.getCallsFromSelf();
    return callsFromSelf.stream()
        .anyMatch(c -> c.getTarget().getOwner().equals(serviceField.getRawType()));
  }

  /**
   * Verifies if the first call to the service field is the {@link ApiServices#withAuthentication}
   * method. It does so by getting all the calls this method makes and filtering it to ensure we are
   * only working with calls to the service field. After this we sort the calls to ensure the {@link
   * ApiServices#withAuthentication} call becomes the first in the list. Finally, we only have to
   * ensure the first call in the list is to the {@link ApiServices#withAuthentication} method.
   *
   * <p>It is important to note that this method assumes that the service field is only called once,
   * and chained afterward. If there is multiple separate calls to the service field it will not be
   * accurate and should be modified! At the time of writing this we did not have such use-cases.
   *
   * @param method The method to check
   * @param serviceField The service field that should call {@link ApiServices#withAuthentication}
   *     first
   * @return true if the method calls {@link ApiServices#withAuthentication} first, false otherwise
   */
  private static boolean callsWithAuthenticationFirst(
      final JavaMethod method, final JavaField serviceField) {
    final var firstServiceCall =
        method.getCallsFromSelf().stream()
            .filter(call -> call.getTarget().getOwner().equals(serviceField.getRawType()))
            .sorted(
                Comparator.<JavaCall<?>>comparingInt(JavaAccess::getLineNumber)
                    .thenComparing(
                        (call1, call2) -> {
                          if (call1.getName().equals(WITH_AUTHENTICATION_METHOD_NAME)) {
                            return -1;
                          } else if (call2.getName().equals(WITH_AUTHENTICATION_METHOD_NAME)) {
                            return 1;
                          } else {
                            return 0;
                          }
                        }))
            .toList()
            .getFirst();

    return firstServiceCall.getName().equals(WITH_AUTHENTICATION_METHOD_NAME);
  }
}

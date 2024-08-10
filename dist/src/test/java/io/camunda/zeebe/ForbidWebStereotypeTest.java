/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Controller;

/**
 * This ArchUnit test ensures any endpoints created in this module is using the actuator annotations
 * and not the normal web binding annotations. Since we have both a normal web server for the REST
 * API and a management server, using the appropriate annotations ensure that the right endpoints
 * will be available through the right server. This means:
 *
 * <ul>
 *   <li>Endpoints with actuator annotations, like {@link
 *       org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint} will be available
 *       through the management server
 *   <li>Endpoints with web stereotypes, like {@link Controller} or {@link
 *       org.springframework.web.bind.annotation.RestController} will be available through the main
 *       web server
 * </ul>
 *
 * As all the REST endpoints will mapped in their own modules, the only endpoints in the packages
 * below should be actuators.
 */
@AnalyzeClasses(
    packages = {"io.camunda.zeebe.broker", "io.camunda.zeebe.gateway", "io.camunda.zeebe.shared"},
    importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeArchives.class})
public final class ForbidWebStereotypeTest {
  private static final DescribedPredicate<? super JavaAnnotation<?>> WEB_STEREOTYPES =
      new DescribedPredicate<>("spring web annotations") {
        @Override
        public boolean test(final JavaAnnotation<?> javaAnnotation) {
          final var packageName = javaAnnotation.getRawType().getPackageName();
          return packageName.startsWith("org.springframework.web.bind.annotation");
        }
      };

  @SuppressWarnings("unused")
  @ArchTest
  public static final ArchRule RULE_FORBID_WEB_STEREOTYPES =
      noClasses()
          .that()
          .resideInAnyPackage(
              "io.camunda.zeebe.broker..",
              "io.camunda.zeebe.gateway..",
              "io.camunda.zeebe.shared..")
          .and()
          .resideOutsideOfPackage("io.camunda.zeebe.gateway.rest")
          .should()
          .beAnnotatedWith(WEB_STEREOTYPES)
          .orShould()
          .beAnnotatedWith(Controller.class)
          .as("should not use any of the web stereotypes to avoid showing up in the REST API");
}

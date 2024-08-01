/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "io.camunda.zeebe.gateway.rest",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class RestControllerAnnotationTest {

  /**
   * This ArchUnit test ensures that any REST API controllers, i.e. classes annotated with the
   * {@link RestController} or {@link Controller} annotation, are also annotated with {@link
   * ConditionalOnRestGatewayEnabled}. This is to ensure that the REST API is not enabled when the
   * Zeebe Gateway is disabled. This setup might happen when an embedded Zeebe Gateway is used in
   * the standalone Zeebe Broker.
   */
  @ArchTest
  public static final ArchRule RULE_DISABLE_REST_API =
      ArchRuleDefinition.classes()
          .that()
          .resideInAnyPackage("io.camunda.zeebe.gateway.rest..")
          .and()
          .areAnnotatedWith(RestController.class)
          .or()
          .areAnnotatedWith(Controller.class)
          .should()
          .beAnnotatedWith(ConditionalOnRestGatewayEnabled.class);
}

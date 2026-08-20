/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.archunit.IncludeTestClassesAndTestJars;
import org.springframework.test.web.reactive.server.WebTestClient.BodyContentSpec;

/**
 * Scans test classes from {@code zeebe-gateway-rest} (pulled in as a test-jar dependency); the
 * {@link IncludeTestClassesAndTestJars} import option ensures classes inside {@code *-tests.jar}
 * entries are picked up alongside this module's own {@code target/test-classes}.
 */
@AnalyzeClasses(
    packages = "io.camunda.zeebe.gateway.rest",
    importOptions = IncludeTestClassesAndTestJars.class)
public class ControllerStrictJsonCompareArchTest {

  /** This ArchUnit test ensures that any REST API controller tests use JsonCompareMode.STRICT */
  @ArchTest
  public static final ArchRule RULE_USE_STRICT_JSON_COMPARISON =
      ArchRuleDefinition.noClasses()
          .should()
          .callMethod(BodyContentSpec.class, "json", String.class);
}

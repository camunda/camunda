/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */ package io.camunda.zeebe.shared;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(
    packages = "io.camunda.zeebe.gateway.rest",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class RestApiDisabledTest {

  @ArchTest
  public static final ArchRule RULE_DISABLE_REST_API =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAnyPackage("io.camunda.zeebe.gateway.rest..")
          .and()
          .areNotInterfaces()
          .should()
          .beAnnotatedWith(RestController.class);
}

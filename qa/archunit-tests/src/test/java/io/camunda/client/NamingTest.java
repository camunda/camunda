/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.client;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
    packages = "io.camunda.client",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class NamingTest {

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_METHODS_SHOULD_NOT_CONTAIN_QUERY =
      noMethods()
          .that()
          .areDeclaredIn(CamundaClient.class)
          .should()
          .haveNameContaining("Query")
          .because("Client API methods should use \"SearchRequest\" instead.");

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_CLASSES_SHOULD_NOT_CONTAIN_QUERY =
      noClasses()
          .that()
          .resideOutsideOfPackage("io.camunda.client.protocol.rest..")
          .should()
          .haveSimpleNameContaining("Query")
          .because("Client classes should use \"SearchRequest\" instead.");

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_METHODS_SHOULD_NOT_CONTAIN_FLOW_NODE =
      noMethods()
          .that()
          .areDeclaredIn(CamundaClient.class)
          .should()
          .haveNameContaining("flowNode")
          .orShould()
          .haveNameContaining("FlowNode")
          .orShould()
          .haveNameContaining("Flownode")
          .orShould()
          .haveNameContaining("flownode")
          .because("Client API methods should use \"Element\" instead.");

  @ArchTest
  public static final ArchRule RULE_CLIENT_API_CLASSES_SHOULD_NOT_CONTAIN_FLOW_NODE =
      noClasses()
          .that()
          .resideOutsideOfPackage("io.camunda.client.protocol.rest..")
          .should()
          .haveSimpleNameContaining("flowNode")
          .orShould()
          .haveSimpleNameContaining("FlowNode")
          .orShould()
          .haveSimpleNameContaining("Flownode")
          .orShould()
          .haveSimpleNameContaining("flownode")
          .because("Client classes should use \"Element\" instead.");
}

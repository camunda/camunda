/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.tool.ToolDescriptions;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * ArchUnit tests to ensure consistent annotation patterns for MCP tool classes.
 *
 * <p>These rules verify that all *Tools classes in subpackages of io.camunda.gateway.mcp.tool:
 *
 * <ul>
 *   <li>Are Spring beans (annotated with @Component)
 *   <li>Enable parameter validation (annotated with @Validated)
 *   <li>Use @CamundaMcpTool annotation on public methods, not @McpTool
 * </ul>
 */
@AnalyzeClasses(
    packages = "io.camunda.gateway.mcp.tool",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class McpToolsAnnotationArchTest {

  /**
   * Tool classes must be Spring components to be auto-discovered and registered as MCP tools.
   *
   * <p>Excludes ToolDescriptions which is a utility class with constants only.
   */
  @ArchTest
  public static final ArchRule RULE_TOOLS_SHOULD_BE_COMPONENTS =
      ArchRuleDefinition.classes()
          .that()
          .resideInAnyPackage("io.camunda.gateway.mcp.tool..")
          .and()
          .haveSimpleNameEndingWith("Tools")
          .and()
          .areNotAssignableFrom(ToolDescriptions.class)
          .should()
          .beAnnotatedWith(Component.class)
          .because("MCP tool classes must be Spring components for auto-discovery");

  /**
   * Tool classes must be annotated with @Validated to enable bean validation on method parameters.
   *
   * <p>Excludes ToolDescriptions which is a utility class with constants only.
   */
  @ArchTest
  public static final ArchRule RULE_TOOLS_SHOULD_BE_VALIDATED =
      ArchRuleDefinition.classes()
          .that()
          .resideInAnyPackage("io.camunda.gateway.mcp.tool..")
          .and()
          .haveSimpleNameEndingWith("Tools")
          .and()
          .areNotAssignableFrom(ToolDescriptions.class)
          .should()
          .beAnnotatedWith(Validated.class)
          .because("MCP tool classes must enable parameter validation with @Validated");

  /**
   * Public methods in tool classes should use @CamundaMcpTool annotation instead of @McpTool.
   *
   * <p>We use @CamundaMcpTool for consistent schema generation and output handling control.
   */
  @ArchTest
  public static final ArchRule RULE_METHODS_SHOULD_USE_CAMUNDA_MCP_TOOL =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage("io.camunda.gateway.mcp.tool..")
          .and()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("Tools")
          .and()
          .arePublic()
          .should()
          .beAnnotatedWith(CamundaMcpTool.class)
          .andShould()
          .notBeAnnotatedWith(McpTool.class)
          .because("MCP tools should use @CamundaMcpTool for consistent schema generation");
}

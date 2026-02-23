/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.tool.ToolDescriptions;
import jakarta.validation.Valid;
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

  /**
   * Parameters annotated with @McpToolParams must also be annotated with @Valid to trigger
   * cascading bean validation on the unwrapped DTO.
   */
  @ArchTest
  public static final ArchRule RULE_MCP_TOOL_PARAMS_SHOULD_BE_VALID =
      ArchRuleDefinition.methods()
          .that()
          .areDeclaredInClassesThat()
          .resideInAnyPackage("io.camunda.gateway.mcp.tool..")
          .and()
          .areDeclaredInClassesThat()
          .haveSimpleNameEndingWith("Tools")
          .and()
          .arePublic()
          .should(
              new ArchCondition<>(
                  "have @Valid on every parameter annotated with @McpToolParamsUnwrapped") {
                @Override
                public void check(final JavaMethod method, final ConditionEvents events) {
                  for (final JavaParameter param : method.getParameters()) {
                    final boolean hasMcpToolParams =
                        param.isAnnotatedWith(McpToolParamsUnwrapped.class);
                    final boolean hasValid = param.isAnnotatedWith(Valid.class);
                    if (hasMcpToolParams && !hasValid) {
                      events.add(
                          SimpleConditionEvent.violated(
                              method,
                              "Parameter %d (%s) of method %s is annotated with @McpToolParamsUnwrapped but missing @Valid"
                                  .formatted(
                                      param.getIndex(),
                                      param.getRawType().getSimpleName(),
                                      method.getFullName())));
                    }
                  }
                }
              })
          .because(
              "@McpToolParamsUnwrapped DTOs must be annotated with @Valid to trigger cascading bean validation");
}

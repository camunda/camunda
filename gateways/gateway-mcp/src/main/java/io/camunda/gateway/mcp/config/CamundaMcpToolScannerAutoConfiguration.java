/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.config;

import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import java.lang.annotation.Annotation;
import java.util.Set;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractAnnotatedMethodBeanPostProcessor;
import org.springframework.ai.mcp.annotation.spring.scan.AbstractMcpAnnotatedBeans;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration for scanning beans annotated with {@link CamundaMcpTool}.
 *
 * <p>This configuration registers:
 *
 * <ul>
 *   <li>{@link CamundaMcpToolAnnotatedBeans} - Registry for discovered tool beans
 *   <li>{@link CamundaMcpToolBeanPostProcessor} - Post-processor that discovers and registers tool
 *       beans during initialization
 * </ul>
 *
 * <p>This follows the same pattern as Spring AI's annotation scanner, ensuring proper integration
 * with Spring's bean lifecycle.
 */
@AutoConfiguration
@ConditionalOnMcpGatewayEnabled
public class CamundaMcpToolScannerAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CamundaMcpToolAnnotatedBeans camundaMcpToolAnnotatedBeans() {
    return new CamundaMcpToolAnnotatedBeans();
  }

  @Bean
  @ConditionalOnMissingBean
  public static CamundaMcpToolBeanPostProcessor camundaMcpToolBeanPostProcessor(
      final CamundaMcpToolAnnotatedBeans registry) {
    return new CamundaMcpToolBeanPostProcessor(registry, Set.of(CamundaMcpTool.class));
  }

  public static class CamundaMcpToolAnnotatedBeans extends AbstractMcpAnnotatedBeans {}

  public static class CamundaMcpToolBeanPostProcessor
      extends AbstractAnnotatedMethodBeanPostProcessor {

    public CamundaMcpToolBeanPostProcessor(
        final CamundaMcpToolAnnotatedBeans registry,
        final Set<Class<? extends Annotation>> targetAnnotations) {
      super(registry, targetAnnotations);
    }
  }
}

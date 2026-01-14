/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.application.initializers.McpGatewayInitializer;
import io.camunda.gateway.mcp.tool.cluster.ClusterTools;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.TopologyServices;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class McpGatewayConfigurationTest {

  private static final Class<ClusterTools> TEST_TOOL_BEAN_CLASS = ClusterTools.class;

  private WebApplicationContextRunner baseRunner() {
    return new WebApplicationContextRunner()
        .withInitializer(new McpGatewayInitializer())
        .withUserConfiguration(McpGatewayConfiguration.class);
  }

  @Test
  public void doesNotIncludeMcpToolComponentsByDefault() {
    baseRunner().run(context -> assertThat(context).doesNotHaveBean(TEST_TOOL_BEAN_CLASS));
  }

  @Test
  public void includesMcpToolComponentsWhenEnabled() {
    baseRunner()
        .withBean(TopologyServices.class, () -> mock(TopologyServices.class))
        .withBean(IncidentServices.class, () -> mock(IncidentServices.class))
        .withBean(JobServices.class, () -> mock(JobServices.class))
        .withBean(
            CamundaAuthenticationProvider.class, () -> mock(CamundaAuthenticationProvider.class))
        .withPropertyValues("camunda.mcp.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(TEST_TOOL_BEAN_CLASS));
  }

  @Test
  public void doesNotIncludeMcpToolComponentsWhenDisabled() {
    baseRunner()
        .withPropertyValues("camunda.mcp.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(TEST_TOOL_BEAN_CLASS));
  }

  @Test
  public void doesNotIncludeMcpToolComponentsWhenBrokerGatewayIsDisabled() {
    baseRunner()
        .withPropertyValues("zeebe.broker.gateway.enable=false", "camunda.mcp.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean(TEST_TOOL_BEAN_CLASS));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.metrics.ServerRequestObservationConfiguration.ServerRequestLowCardinalityKeyValuesMapper;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.gateway.mcp"})
@ConditionalOnMcpGatewayEnabled
public class McpGatewayConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(McpGatewayConfiguration.class);

  private static final String KEY_URI = "uri";
  private static final String URI_MCP = "/mcp";
  public static final KeyValues MCP_BASE_URI_VALUE = KeyValues.of(KeyValue.of(KEY_URI, URI_MCP));

  @Bean
  public ServerRequestLowCardinalityKeyValuesMapper lowCardinalityKeyValuesProvider(
      final ObjectMapper objectMapper) {
    return context -> {
      final HttpServletRequest carrier = context.getCarrier();
      if (carrier == null) {
        return KeyValues.empty();
      }

      if (!URI_MCP.equals(carrier.getServletPath())) {
        // don't adjust the URI tag value
        return KeyValues.empty();
      }

      if (!(HttpMethod.POST.matches(carrier.getMethod())
          && carrier instanceof final ContentCachingRequestWrapper cachedContent)) {
        // we cannot use the request body to further derive the URI tag value
        return MCP_BASE_URI_VALUE;
      }
      try {
        final String requestBody = cachedContent.getContentAsString();
        if (requestBody.isEmpty()) {
          // there is nothing we can further derive from the request
          return MCP_BASE_URI_VALUE;
        }

        // fetch MCP metadata to enrich the URI tag as much as possible
        final McpRequestMetadata mcpMetadata =
            objectMapper.readValue(requestBody, McpRequestMetadata.class);
        if (mcpMetadata == null) {
          // there is no metadata we could use
          return MCP_BASE_URI_VALUE;
        }
        return KeyValues.of(KeyValue.of(KEY_URI, getMcpUri(mcpMetadata)));
      } catch (final Exception ex) {
        LOGGER.warn("Unable to handle MCP request correctly", ex);
        return MCP_BASE_URI_VALUE;
      }
    };
  }

  private static String getMcpUri(final McpRequestMetadata mcpMetadata) {
    final String methodName = mcpMetadata.method();
    if (methodName == null || methodName.isEmpty()) {
      return URI_MCP;
    }
    final String mcpUriWithMethod = URI_MCP + "/" + methodName;
    if (mcpMetadata.params() == null
        || mcpMetadata.params().name() == null
        || mcpMetadata.params().name().isEmpty()) {
      return mcpUriWithMethod;
    }
    return mcpUriWithMethod + "/" + mcpMetadata.params().name();
  }
}

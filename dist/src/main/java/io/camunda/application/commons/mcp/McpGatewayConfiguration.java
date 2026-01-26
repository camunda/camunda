/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.commons.rest.RestApiConfiguration;
import io.camunda.gateway.mcp.ConditionalOnMcpGatewayEnabled;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = {"io.camunda.gateway.mcp"})
@ConditionalOnMcpGatewayEnabled
public class McpGatewayConfiguration {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestApiConfiguration.class);

  private static final String KEY_URI = "uri";
  private static final String MCP_URI = "/mcp";

  @Bean
  public Function<ServerRequestObservationContext, KeyValues> lowCardinalityKeyValuesProvider(
      final ObjectMapper objectMapper) {
    return context -> {
      final HttpServletRequest carrier = context.getCarrier();

      // fetch MCP metadata to enrich the URI tag as much as possible
      if (MCP_URI.equals(carrier.getServletPath())) {
        var mcpUri = MCP_URI;
        try {
          if (HttpMethod.POST.matches(carrier.getMethod())
              && carrier instanceof final ContentCachingRequestWrapper cachedRequest) {
            final var requestBody = cachedRequest.getContentAsString();
            if (!requestBody.isEmpty()) {
              final var mcpMetadata = objectMapper.readValue(requestBody, McpRequestMetadata.class);
              if (mcpMetadata.method() != null) {
                mcpUri = mcpUri + "/" + mcpMetadata.method();
                if (mcpMetadata.params() != null) {
                  final var toolName = mcpMetadata.params().name();
                  if (toolName != null) {
                    mcpUri = mcpUri + "/" + toolName;
                  }
                }
                return KeyValues.of(KeyValue.of(KEY_URI, mcpUri));
              }
            }
          }
        } catch (final Exception ex) {
          LOGGER.warn("Unable to handle MCP request correctly", ex);
        }
        return KeyValues.of(KeyValue.of(KEY_URI, mcpUri));
      }

      return KeyValues.empty();
    };
  }
}

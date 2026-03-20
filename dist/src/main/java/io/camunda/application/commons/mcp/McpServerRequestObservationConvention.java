/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.mcp;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.lang.NonNull;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Extends the default server request observation convention to add MCP-specific data to the URI tag
 * in server request metrics. This applies to all requests under the "/mcp" path. Other requests are
 * not affected.
 */
public class McpServerRequestObservationConvention
    extends DefaultServerRequestObservationConvention {

  public static final String URI_MCP_PREFIX = "/mcp";

  private static final Logger LOGGER =
      LoggerFactory.getLogger(McpServerRequestObservationConvention.class);

  private static final String KEY_URI = "uri";

  private final JsonMapper jsonMapper;

  public McpServerRequestObservationConvention(final String name, final JsonMapper jsonMapper) {
    super(name);
    this.jsonMapper = jsonMapper;
  }

  /** Extends the default low cardinality key values to include MCP-specific URI information. */
  @Override
  public @NonNull KeyValues getLowCardinalityKeyValues(
      final @NonNull ServerRequestObservationContext context) {
    final var lowCardinalityKeyValues = super.getLowCardinalityKeyValues(context);
    final var newLowCardinalityKeyValues = getMcpRequestLowCardinalityValues(context, jsonMapper);
    return lowCardinalityKeyValues.and(newLowCardinalityKeyValues);
  }

  protected static KeyValues getMcpRequestLowCardinalityValues(
      final ServerRequestObservationContext context, final JsonMapper jsonMapper) {
    final HttpServletRequest carrier = context.getCarrier();
    if (carrier == null || !isMcpRequest(carrier)) {
      // don't adjust the URI tag value
      return KeyValues.empty();
    }

    final String servletPath = carrier.getServletPath();
    final KeyValues baseMcpUriValue = KeyValues.of(KeyValue.of(KEY_URI, servletPath));

    try {
      final String requestBody = getRequestBody(carrier);
      if (requestBody.isEmpty()) {
        // there is nothing we can further derive from the request
        return baseMcpUriValue;
      }

      // fetch MCP metadata to enrich the URI tag as much as possible
      final McpRequestMetadata mcpMetadata =
          jsonMapper.readValue(requestBody, McpRequestMetadata.class);
      if (mcpMetadata == null) {
        // there is no metadata we could use
        return baseMcpUriValue;
      }
      return KeyValues.of(KeyValue.of(KEY_URI, getMcpUri(servletPath, mcpMetadata)));
    } catch (final Exception ex) {
      LOGGER.warn("Unable to handle MCP request correctly", ex);
      return baseMcpUriValue;
    }
  }

  private static boolean isMcpRequest(final HttpServletRequest carrier) {
    final String servletPath = carrier.getServletPath();
    return servletPath != null && servletPath.startsWith(URI_MCP_PREFIX);
  }

  private static String getRequestBody(final HttpServletRequest carrier) {
    if (carrier instanceof final ContentCachingRequestWrapper cachingCarrier) {
      return cachingCarrier.getContentAsString();
    }
    // we cannot read the body without failing other filters down the line
    return "";
  }

  private static String getMcpUri(final String basePath, final McpRequestMetadata mcpMetadata) {
    final String methodName = mcpMetadata.method();
    if (methodName == null || methodName.isEmpty()) {
      return basePath;
    }
    final String mcpUriWithMethod = basePath + "/" + methodName;
    if (mcpMetadata.params() == null
        || mcpMetadata.params().name() == null
        || mcpMetadata.params().name().isEmpty()) {
      return mcpUriWithMethod;
    }
    return mcpUriWithMethod + "/" + mcpMetadata.params().name();
  }
}

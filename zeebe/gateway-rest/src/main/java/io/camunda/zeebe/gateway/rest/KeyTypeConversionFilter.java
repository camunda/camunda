/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;

// @Component
// @Order(1)
public class KeyTypeConversionFilter implements Filter {
  private static final String VERSION_HEADER = "X-Camunda-API-Version";

  private final ObjectMapper om = new ObjectMapper();

  @Override
  public void doFilter(
      final ServletRequest request, final ServletResponse response, final FilterChain chain)
      throws ServletException, IOException {

    if (request instanceof final HttpServletRequest httpServletRequest) {
      if (httpServletRequest.getHeader(VERSION_HEADER) == null) {
        chain.doFilter(request, response);
        return;
      }

      final ContentCachingResponseWrapper responseWrapper =
          new ContentCachingResponseWrapper((HttpServletResponse) response);
      chain.doFilter(request, responseWrapper);

      if (!MimeTypeUtils.parseMimeType(responseWrapper.getContentType())
          .equals(MimeTypeUtils.APPLICATION_JSON)) {
        responseWrapper.getContentInputStream().transferTo(response.getOutputStream());
        return;
      }

      final JsonNode mappedResult = map(om.readTree(responseWrapper.getContentAsByteArray()));
      om.writeValue(response.getWriter(), mappedResult);
    }
  }

  private JsonNode map(final JsonNode node) {
    return switch (node.getNodeType()) {
      case ARRAY -> {
        final var mappedArray = om.createArrayNode();
        node.iterator().forEachRemaining(element -> mappedArray.add(map(element)));
        yield mappedArray;
      }
      case OBJECT -> {
        final var mappedObject = om.createObjectNode();
        node.fields()
            .forEachRemaining(
                entry ->
                    mappedObject.set(
                        entry.getKey(), mapObjectValue(entry.getKey(), entry.getValue())));
        yield mappedObject;
      }
      default -> node;
    };
  }

  private JsonNode mapObjectValue(final String name, final JsonNode value) {
    return value.getNodeType() == JsonNodeType.NUMBER
            && (name.equals("key") || name.endsWith("Key"))
        ? om.getNodeFactory().textNode(Long.toString(value.longValue()))
        : value;
  }
}

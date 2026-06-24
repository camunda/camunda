/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.ai.util.json.JsonParser;
import tools.jackson.databind.json.JsonMapper;

public final class CallToolResultAssertions {

  private static final JsonMapper NON_NULL_MAPPER =
      JsonParser.getJsonMapper()
          .rebuild()
          .changeDefaultPropertyInclusion(
              incl ->
                  incl.withContentInclusion(JsonInclude.Include.NON_NULL)
                      .withValueInclusion(JsonInclude.Include.NON_NULL))
          .build();

  private CallToolResultAssertions() {}

  /**
   * Asserts that the structured content and the text content fallback of a {@link CallToolResult}
   * are equivalent JSON, ignoring null fields. This is necessary because the MCP transport
   * round-trip drops null values from the structured content map.
   */
  public static void assertTextContentFallback(final CallToolResult result) {
    assertThat(result.structuredContent()).isNotNull();
    assertThat(result.content()).isNotNull().hasSize(1).first().isInstanceOf(TextContent.class);

    final var textContent = ((TextContent) result.content().getFirst()).text();

    try {
      JSONAssert.assertEquals(
          NON_NULL_MAPPER.writeValueAsString(NON_NULL_MAPPER.readValue(textContent, Object.class)),
          NON_NULL_MAPPER.writeValueAsString(result.structuredContent()),
          true);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to compare structuredContent to text content fallback", e);
    }
  }
}

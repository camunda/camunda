/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper;

import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.http.ProblemDetail;

public class CallToolResultMapper {
  public static CallToolResult from(final Object content) {
    return CallToolResult.builder().structuredContent(content).build();
  }

  public static CallToolResult mapErrorToResult(final Throwable error) {
    // TODO is the rest to problem mapper sufficient here?
    return mapProblemToResult(RestErrorMapper.mapErrorToProblem(error));
  }

  public static CallToolResult mapProblemToResult(final ProblemDetail problemDetail) {
    // TODO how widely supported is structured content in MCP clients?
    return CallToolResult.builder().structuredContent(problemDetail).isError(true).build();
  }
}

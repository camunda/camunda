/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.model;

import io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import org.springframework.lang.Nullable;

public class McpSearchQueryPageRequest extends SearchQueryPageRequest {

  @Schema(
      name = "after",
      example = "WzIyNTE3OTk4MTM2ODcxMDJd",
      description =
          "Use the `endCursor` value from the previous response to fetch the next page of results.",
      requiredMode = RequiredMode.NOT_REQUIRED)
  @Override
  public @Nullable String getAfter() {
    return super.getAfter();
  }

  @Schema(
      name = "before",
      example = "WzIyNTE3OTk4MTM2ODcxMDJd",
      description =
          "Use the `startCursor` value from the previous response to fetch the previous page of results.",
      requiredMode = RequiredMode.NOT_REQUIRED)
  @Override
  public @Nullable String getBefore() {
    return super.getBefore();
  }
}

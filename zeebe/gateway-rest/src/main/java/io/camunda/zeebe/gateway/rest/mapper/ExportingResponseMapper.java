/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import io.camunda.gateway.protocol.model.ExportingStatusCode;
import io.camunda.gateway.protocol.model.ExportingStatusResponse;
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ExportingResponseMapper {

  private ExportingResponseMapper() {}

  public static ExportingStatusResponse toExportingStatusResponse(final ExportingStatus status) {
    return ExportingStatusResponse.Builder.create().status(toStatusCode(status)).build();
  }

  private static ExportingStatusCode toStatusCode(final ExportingStatus status) {
    return switch (status) {
      case EXPORTING -> ExportingStatusCode.EXPORTING;
      case PAUSED -> ExportingStatusCode.PAUSED;
      case SOFT_PAUSED -> ExportingStatusCode.SOFT_PAUSED;
      case MIXED -> ExportingStatusCode.MIXED;
    };
  }
}

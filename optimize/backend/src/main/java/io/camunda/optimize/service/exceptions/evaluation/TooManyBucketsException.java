/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions.evaluation;

import io.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;

public class TooManyBucketsException extends ReportEvaluationException {

  public static final String ERROR_CODE = "tooManyBuckets";

  public TooManyBucketsException(
      final AuthorizedReportDefinitionResponseDto reportDefinition, final Exception e) {
    super(reportDefinition, e);
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}

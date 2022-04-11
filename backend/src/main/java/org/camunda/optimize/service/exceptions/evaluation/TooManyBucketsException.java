/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions.evaluation;

import org.camunda.optimize.dto.optimize.rest.AuthorizedReportDefinitionResponseDto;

public class TooManyBucketsException extends ReportEvaluationException {
  public static final String ERROR_CODE = "tooManyBuckets";

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }

  public TooManyBucketsException(AuthorizedReportDefinitionResponseDto reportDefinition, Exception e) {
    super(reportDefinition, e);
  }

}

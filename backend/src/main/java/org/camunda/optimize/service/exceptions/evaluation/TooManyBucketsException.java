/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.exceptions.evaluation;

public class TooManyBucketsException extends ReportEvaluationException {

  @Override
  public String getErrorCode() {
    return "tooManyBuckets";
  }

  public TooManyBucketsException(Exception e) {
    super(e.getMessage(), e);
  }

}

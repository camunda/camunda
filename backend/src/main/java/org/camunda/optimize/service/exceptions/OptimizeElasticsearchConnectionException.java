/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

public class OptimizeElasticsearchConnectionException extends OptimizeRuntimeException {
  public static final String ERROR_CODE = "elasticsearchConnectionError";

  public OptimizeElasticsearchConnectionException(String message, Throwable e) {
    super(message, e);
  }

  @Override
  public String getErrorCode() {
    return ERROR_CODE;
  }
}

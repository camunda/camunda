/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.exceptions;

public class OperateRuntimeException extends RuntimeException {

  public OperateRuntimeException() {
  }

  public OperateRuntimeException(String message) {
    super(message);
  }

  public OperateRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public OperateRuntimeException(Throwable cause) {
    super(cause);
  }
}

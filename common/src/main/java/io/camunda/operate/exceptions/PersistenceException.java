/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.exceptions;


public class PersistenceException extends Exception {

  private static final long serialVersionUID = 1L;

  private Integer failingRequestId;

  public PersistenceException() {
  }

  public PersistenceException(String message) {
    super(message);
  }

  public PersistenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public PersistenceException(String message, Throwable cause, Integer failingRequestId) {
    super(message, cause);
    this.failingRequestId = failingRequestId;
  }

  public PersistenceException(Throwable cause) {
    super(cause);
  }

  public Integer getFailingRequestId() {
    return failingRequestId;
  }

  public void setFailingRequestId(Integer failingRequestId) {
    this.failingRequestId = failingRequestId;
  }
}

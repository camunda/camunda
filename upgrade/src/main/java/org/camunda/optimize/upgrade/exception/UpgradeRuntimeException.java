/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.exception;


public class UpgradeRuntimeException extends RuntimeException {
  public UpgradeRuntimeException(String message) {
    super(message);
  }

  public UpgradeRuntimeException(Exception e) {
    super(e);
  }

  public UpgradeRuntimeException(String message, Exception e) {
    super(message, e);
  }
}

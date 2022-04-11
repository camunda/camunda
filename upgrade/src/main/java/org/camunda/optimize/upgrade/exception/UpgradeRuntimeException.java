/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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

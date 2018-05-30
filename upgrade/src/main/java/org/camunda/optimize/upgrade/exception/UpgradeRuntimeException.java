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

package org.camunda.optimize.upgrade.exception;

/**
 * @author Askar Akhmerov
 */
public class UpgradeRuntimeException extends RuntimeException {
  public UpgradeRuntimeException(String message) {
    super(message);
  }

  public UpgradeRuntimeException(Exception e) {
    super(e);
  }
}

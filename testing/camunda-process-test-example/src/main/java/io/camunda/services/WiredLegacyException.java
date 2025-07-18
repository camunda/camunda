package io.camunda.services;

public class WiredLegacyException extends Exception {

  private static final long serialVersionUID = 1L;

  public WiredLegacyException() {
    super(
        "The legacy system has wired hiccups so there might be strange errors like this from time to time");
  }

  public WiredLegacyException(String message) {
    super(message);
  }
}

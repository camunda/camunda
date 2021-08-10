package io.camunda.zeebe.util.startup.actor;

public class BootstrapStepException extends Exception {
  private final String stepName;

  public BootstrapStepException(final Throwable cause, final String stepName) {
    super(String.format("Bootstrap step %s failed", stepName), cause);
    this.stepName = stepName;
  }

  public String getStepName() {
    return stepName;
  }
}

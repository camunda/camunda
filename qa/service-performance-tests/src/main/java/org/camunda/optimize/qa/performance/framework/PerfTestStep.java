package org.camunda.optimize.qa.performance.framework;

public abstract class PerfTestStep {

  public Class getTestStepClass() {
    return this.getClass();
  }

  public abstract PerfTestStepResult execute(PerfTestContext  context);

}

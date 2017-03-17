package org.camunda.optimize.qa.performance.framework;

public class PerfTestBuilder {

  private PerfTest perfTest;

  public PerfTestBuilder(PerfTestConfiguration configuration) {
    perfTest = new PerfTest(configuration);
  }

  public static PerfTestBuilder createPerfTest(PerfTestConfiguration configuration) {
    return new PerfTestBuilder(configuration);
  }

  public PerfTestBuilder step(PerfTestStep step) {
    perfTest.addTestStep(step);
    return this;
  }

  public PerfTest done() {
    return perfTest;
  }
}

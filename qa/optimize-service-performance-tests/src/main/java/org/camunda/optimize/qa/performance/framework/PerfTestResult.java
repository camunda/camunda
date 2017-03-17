package org.camunda.optimize.qa.performance.framework;

import java.util.HashMap;
import java.util.Map;

public class PerfTestResult {

  /**
   * Maps the performance step class to its result
   */
  private Map<Class, PerfTestStepResult> results = new HashMap<>();

  public void addStepResult(Class testStepClass, PerfTestStepResult value) {
    results.put(testStepClass, value);
  }

  public <T> PerfTestStepResult getResult(Class testStepClass) {
      return results.get(testStepClass);
  }

}

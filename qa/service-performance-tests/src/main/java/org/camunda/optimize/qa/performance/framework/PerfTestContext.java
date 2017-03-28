package org.camunda.optimize.qa.performance.framework;

import java.util.HashMap;
import java.util.Map;

public class PerfTestContext {

  private PerfTestConfiguration configuration;

  private Map<String, Object> parameter;

  public PerfTestContext(PerfTestConfiguration configuration) {
    this.configuration = configuration;
    parameter =  new HashMap<>();
  }

  public void addParameter(String name, Object value) {
    parameter.put(name, value);
  }

  public Object getParameter(String name) {
    return parameter.get(name);
  }

  public PerfTestConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(PerfTestConfiguration configuration) {
    this.configuration = configuration;
  }
}

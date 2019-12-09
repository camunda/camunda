package io.zeebe.broker.bootstrap;

import java.util.function.Supplier;

public class StartStep {

  private final String name;
  private final StartFunction startFunction;

  public StartStep(String name, StartFunction startFunction) {
    this.name = name;
    this.startFunction = startFunction;
  }

  public String getName() {
    return name;
  }

  public StartFunction getStartFunction() {
    return startFunction;
  }
}

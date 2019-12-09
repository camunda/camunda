package io.zeebe.broker.bootstrap;

public class CloseStep {

  private final String name;
  private final AutoCloseable closingFunction;

  public CloseStep(String name, AutoCloseable closingFunction) {
    this.name = name;
    this.closingFunction = closingFunction;
  }

  public String getName() {
    return name;
  }

  public AutoCloseable getClosingFunction() {
    return closingFunction;
  }
}

package io.camunda.zeebe.spring.client.jobhandling;

public interface CommandExceptionHandlingStrategy {

  public void handleCommandError(CommandWrapper command, Throwable throwable);
}

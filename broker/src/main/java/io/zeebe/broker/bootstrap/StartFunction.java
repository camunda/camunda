package io.zeebe.broker.bootstrap;

@FunctionalInterface
public interface StartFunction {
  AutoCloseable start() throws Exception;
}

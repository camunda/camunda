package io.zeebe.broker.bootstrap;

@FunctionalInterface
public interface Runner {
  void run() throws Exception;
}

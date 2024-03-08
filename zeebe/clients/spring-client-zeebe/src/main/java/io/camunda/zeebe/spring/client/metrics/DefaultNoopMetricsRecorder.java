package io.camunda.zeebe.spring.client.metrics;

/**
 * Default implementation for MetricsRecorder simply ignoring the counts. Typically you will replace
 * this by a proper Micrometer implementation as you can find in the starter module (activated if
 * Actuator is on the classpath)
 */
public class DefaultNoopMetricsRecorder implements MetricsRecorder {

  @Override
  public void increase(String metricName, String action, String type, int count) {
    // ignore
  }

  @Override
  public void executeWithTimer(String metricName, String jobType, Runnable methodToExecute) {
    methodToExecute.run();
  }
}

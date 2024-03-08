package io.camunda.zeebe.spring.client.actuator;

import io.camunda.zeebe.spring.client.metrics.MetricsRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MicrometerMetricsRecorder implements MetricsRecorder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final MeterRegistry meterRegistry;
  private final Map<String, Counter> counters = new ConcurrentHashMap<>();

  public MicrometerMetricsRecorder(final MeterRegistry meterRegistry) {
    LOGGER.info("Enabling Micrometer based metrics for spring-zeebe (available via Actuator)");
    this.meterRegistry = meterRegistry;
  }

  protected Counter newCounter(String metricName, String action, String jobType) {
    List<Tag> tags = new ArrayList<>();
    if (action != null && !action.isEmpty()) {
      tags.add(Tag.of("action", action));
    }
    if (jobType != null && !jobType.isEmpty()) {
      tags.add(Tag.of("type", jobType));
    }
    return meterRegistry.counter(metricName, tags);
  }

  @Override
  public void increase(String metricName, String action, String type, int count) {
    String key = metricName + "#" + action + '#' + type;
    Counter counter = counters.computeIfAbsent(key, k -> newCounter(metricName, action, type));
    counter.increment(count);
  }

  @Override
  public void executeWithTimer(String metricName, String jobType, Runnable methodToExecute) {
    Timer timer = meterRegistry.timer(metricName, "type", jobType);
    timer.record(methodToExecute);
  }
}

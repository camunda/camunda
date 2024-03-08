package io.camunda.zeebe.spring.client.jobhandling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Wrapper bean for {@link ScheduledExecutorService} required in Spring Zeebe for Job Handling,
 * Retry Management and so on.
 *
 * <p>This is wrapped so you can have multiple executor services in the Spring context and qualify
 * the right one.
 */
public class ZeebeClientExecutorService {

  private ScheduledExecutorService scheduledExecutorService;

  public ZeebeClientExecutorService(ScheduledExecutorService scheduledExecutorService) {
    this.scheduledExecutorService = scheduledExecutorService;
  }

  public ScheduledExecutorService get() {
    return scheduledExecutorService;
  }

  public static ZeebeClientExecutorService createDefault() {
    return createDefault(1);
  }

  public static ZeebeClientExecutorService createDefault(int threads) {
    ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(threads);
    return new ZeebeClientExecutorService(threadPool);
  }

  /*
  public static ZeebeClientExecutorService createDefault(MeterRegistry meterRegistry) {
    ScheduledExecutorService threadPool = Executors.newSingleThreadScheduledExecutor();
    if (meterRegistry != null) {
      MeterBinder threadPoolMetrics = new ExecutorServiceMetrics(
        threadPool, "zeebe_client_thread_pool", Collections.emptyList());
      threadPoolMetrics.bindTo(meterRegistry);
    }
    return new ZeebeClientExecutorService(threadPool);
  }*/
}

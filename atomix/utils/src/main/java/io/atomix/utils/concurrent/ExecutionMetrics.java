package io.atomix.utils.concurrent;

import io.prometheus.client.Histogram;

public class ExecutionMetrics {

  private static final Histogram EXECUTION_DURATION =
      Histogram.build()
          .namespace("atomix")
          .name("sched_task_execution_latency")
          .help("Job execution time")
          .register();

  private static final Histogram SCHEDULE_LATENCY =
      Histogram.build()
          .namespace("atomix")
          .name("sched_task_schedule_latency")
          .help("Latency to execute scheduled job")
          .register();

  public Histogram.Timer startExecutionTimer() {
    return EXECUTION_DURATION.startTimer();
  }

  public Histogram.Timer startSchedulerTimer() {
    return SCHEDULE_LATENCY.startTimer();
  }
}

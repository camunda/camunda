/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.Queue;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class Metrics {

  private static final Logger logger = LoggerFactory.getLogger(Metrics.class);

  private Timer importBatchTimer;

  // Namespace (prefix) for operate metrics
  public static final String OPERATE_NAMESPACE = "operate.";

  // Timers:
  public static final String TIMER_NAME_QUERY = OPERATE_NAMESPACE + "query";
  public static final String TIMER_NAME_IMPORT_QUERY = OPERATE_NAMESPACE + "import.query";
  public static final String TIMER_NAME_IMPORT_INDEX_QUERY = OPERATE_NAMESPACE + "import.index.query";
  public static final String TIMER_NAME_IMPORT_PROCESS_BATCH = OPERATE_NAMESPACE + "import.process.batch";
  public static final String TIMER_NAME_ARCHIVER_QUERY = OPERATE_NAMESPACE + "archiver.query";
  public static final String TIMER_NAME_ARCHIVER_REINDEX_QUERY = OPERATE_NAMESPACE + "archiver.reindex.query";
  public static final String TIMER_NAME_ARCHIVER_DELETE_QUERY = OPERATE_NAMESPACE + "archiver.delete.query";
  // Counters:
  public static final String COUNTER_NAME_EVENTS_PROCESSED = "events.processed";
  public static final String COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI = "events.processed.finished.process.instances";
  public static final String COUNTER_NAME_COMMANDS = "commands";
  public static final String COUNTER_NAME_ARCHIVED = "archived.process.instances";
  //Gauges:
  public static final String GAUGE_IMPORT_QUEUE_SIZE = "import.queue.size";

  // Tags
  // -----
  //  Keys:
  public static final String TAG_KEY_NAME = "name",
                             TAG_KEY_TYPE = "type",
                             TAG_KEY_PARTITION = "partition",
                             TAG_KEY_STATUS = "status";
  //  Values:
  public static final String TAG_VALUE_PROCESSINSTANCES = "processInstances",
                             TAG_VALUE_CORESTATISTICS = "corestatistics",
                             TAG_VALUE_SUCCEEDED = "succeeded",
                             TAG_VALUE_FAILED = "failed";

  @Autowired
  private MeterRegistry registry;

  /**
   * Record counts for given name and tags. Tags are further attributes that gives the possibility to categorize the counter.
   * They will be given as varargs key value pairs. For example: "type":"incident".
   * Original documentation for tags: <a href="https://micrometer.io/docs/concepts#_tag_naming">Tags naming</a>
   * 
   * @param name - Name of counter
   * @param count - Number to count 
   * @param tags - key value pairs of tags as Strings - The size of tags varargs must be even.
   */
  public void recordCounts(String name, long count, String ... tags) {
    registry.counter(OPERATE_NAMESPACE + name, tags).increment(count);
  }

  public <T> void registerGauge(String name, T stateObject, ToDoubleFunction<T> valueFunction,
      String... tags) {
    Gauge.builder(OPERATE_NAMESPACE + name, stateObject, valueFunction)
        .tags(tags)
        .register(registry);
  }

  public <E> void registerGaugeQueueSize(String name, Queue<E> queue, String... tags) {
    registerGauge(name, queue, q -> q.size(), tags);
  }

  public Timer getTimer(String name) {
    return registry.timer(name);
  }

}

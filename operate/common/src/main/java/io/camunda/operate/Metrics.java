/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Queue;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Metrics {

  // Namespace (prefix) for operate metrics
  public static final String OPERATE_NAMESPACE = "operate.";
  // Timers:
  public static final String TIMER_NAME_QUERY = OPERATE_NAMESPACE + "query";
  public static final String TIMER_NAME_IMPORT_QUERY = OPERATE_NAMESPACE + "import.query";
  public static final String TIMER_NAME_IMPORT_INDEX_QUERY =
      OPERATE_NAMESPACE + "import.index.query";
  public static final String TIMER_NAME_IMPORT_PROCESS_BATCH =
      OPERATE_NAMESPACE + "import.process.batch";
  public static final String TIMER_NAME_IMPORT_TIME = OPERATE_NAMESPACE + "import.time";
  public static final String TIMER_NAME_IMPORT_JOB_SCHEDULED_TIME =
      OPERATE_NAMESPACE + "import.job.scheduled";
  public static final String TIMER_NAME_IMPORT_PROCESSING_DURATION =
      OPERATE_NAMESPACE + "import.processing.duration";
  public static final String TIMER_NAME_IMPORT_POSITION_UPDATE =
      OPERATE_NAMESPACE + "import.position.update";
  public static final String TIMER_NAME_ARCHIVER_QUERY = OPERATE_NAMESPACE + "archiver.query";
  public static final String TIMER_NAME_ARCHIVER_REINDEX_QUERY =
      OPERATE_NAMESPACE + "archiver.reindex.query";
  public static final String TIMER_NAME_ARCHIVER_DELETE_QUERY =
      OPERATE_NAMESPACE + "archiver.delete.query";
  public static final String TIMER_NAME_IMPORT_FNI_TREE_PATH_CACHE_ACCESS =
      OPERATE_NAMESPACE + "import.fni.tree.path.cache.access";
  // Counters:
  public static final String COUNTER_NAME_EVENTS_PROCESSED = "events.processed";
  public static final String COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI =
      "events.processed.finished.process.instances";
  public static final String COUNTER_NAME_COMMANDS = "commands";
  public static final String COUNTER_NAME_ARCHIVED = "archived.process.instances";
  public static final String COUNTER_NAME_IMPORT_FNI_TREE_PATH_CACHE_RESULT =
      "import.fni.tree.path.cache.result";
  public static final String COUNTER_NAME_REINDEX_FAILURES = "archival.reindex.failures";
  public static final String COUNTER_NAME_DELETE_FAILURES = "archival.delete.failures";

  // Gauges:
  public static final String GAUGE_IMPORT_QUEUE_SIZE = OPERATE_NAMESPACE + "import.queue.size";
  public static final String GAUGE_BPMN_MODEL_COUNT = OPERATE_NAMESPACE + "model.bpmn.count";
  public static final String GAUGE_DMN_MODEL_COUNT = OPERATE_NAMESPACE + "model.dmn.count";

  public static final String GAUGE_NAME_IMPORT_FNI_TREE_PATH_CACHE_SIZE =
      OPERATE_NAMESPACE + "import.fni.tree.path.cache.size";

  // Tags
  // -----
  //  Keys:
  public static final String TAG_KEY_NAME = "name",
      TAG_KEY_TYPE = "type",
      TAG_KEY_PARTITION = "partition",
      TAG_KEY_STATUS = "status",
      TAG_KEY_ORGANIZATIONID = "organizationId";
  //  Values:
  public static final String TAG_VALUE_PROCESSINSTANCES = "processInstances",
      TAG_VALUE_CORESTATISTICS = "corestatistics",
      TAG_VALUE_SUCCEEDED = "succeeded",
      TAG_VALUE_FAILED = "failed";
  private static final Logger LOGGER = LoggerFactory.getLogger(Metrics.class);
  private Timer importBatchTimer;
  @Autowired private MeterRegistry registry;

  /**
   * Record counts for given name and tags. Tags are further attributes that gives the possibility
   * to categorize the counter. They will be given as varargs key value pairs. For example:
   * "type":"incident". Original documentation for tags: <a
   * href="https://micrometer.io/docs/concepts#_tag_naming">Tags naming</a>
   *
   * @param name - Name of counter
   * @param count - Number to count
   * @param tags - key value pairs of tags as Strings - The size of tags varargs must be even.
   */
  public void recordCounts(final String name, final long count, final String... tags) {
    registry.counter(OPERATE_NAMESPACE + name, tags).increment(count);
  }

  public <T> void registerGauge(
      final String name,
      final T stateObject,
      final ToDoubleFunction<T> valueFunction,
      final String... tags) {
    Gauge.builder(name, stateObject, valueFunction).tags(tags).register(registry);
  }

  public void registerGaugeSupplier(
      final String name, final Supplier<Number> gaugeSupplier, final String... tags) {
    Gauge.builder(name, gaugeSupplier).tags(tags).register(registry);
  }

  public <E> void registerGaugeQueueSize(
      final String name, final Queue<E> queue, final String... tags) {
    registerGauge(name, queue, q -> q.size(), tags);
  }

  public Timer getTimer(final String name, final String... tags) {
    return registry.timer(name, tags);
  }

  public Timer getHistogram(final String name, final String... tags) {
    return Timer.builder(name).publishPercentileHistogram().tags(tags).register(registry);
  }
}

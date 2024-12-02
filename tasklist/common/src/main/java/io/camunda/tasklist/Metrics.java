/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Metrics {

  // Namespace (prefix) for metrics
  public static final String TASKLIST_NAMESPACE = "tasklist.";
  // Timers:
  public static final String TIMER_NAME_QUERY = TASKLIST_NAMESPACE + "query";
  public static final String TIMER_NAME_IMPORT_QUERY = TASKLIST_NAMESPACE + "import.query";
  public static final String TIMER_NAME_IMPORT_INDEX_QUERY =
      TASKLIST_NAMESPACE + "import.index.query";
  public static final String TIMER_NAME_IMPORT_TIME = TASKLIST_NAMESPACE + "import.time";
  public static final String TIMER_NAME_IMPORT_JOB_SCHEDULED_TIME =
      TASKLIST_NAMESPACE + "import.job.scheduled";
  public static final String TIMER_NAME_IMPORT_POSITION_UPDATE =
      TASKLIST_NAMESPACE + "import.position.update";
  // Counters:
  public static final String COUNTER_NAME_EVENTS_PROCESSED = "events.processed";
  public static final String COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI =
      "events.processed.finished.process.instances";
  public static final String COUNTER_NAME_COMMANDS = "commands";
  public static final String COUNTER_NAME_ARCHIVED = "archived.process.instances";

  public static final String COUNTER_NAME_CLAIMED_TASKS = "claimed.tasks";
  public static final String COUNTER_NAME_COMPLETED_TASKS = "completed.tasks";

  public static final String GAUGE_NAME_IMPORT_POSITION_COMPLETED =
      TASKLIST_NAMESPACE + "import.completed";
  // Tags
  // -----
  //  Keys:
  public static final String TAG_KEY_NAME = "name",
      TAG_KEY_TYPE = "type",
      TAG_KEY_IMPORT_POS_ALIAS = "importPositionAlias",
      TAG_KEY_PARTITION = "partition",
      TAG_KEY_STATUS = "status",
      TAG_KEY_BPMN_PROCESS_ID = "bpmnProcessId",
      TAG_KEY_FLOW_NODE_ID = "flowNodeId",
      TAG_KEY_USER_ID = "userId",
      TAG_KEY_ORGANIZATION_ID = "organizationId";

  //  Values:
  public static final String TAG_VALUE_PROCESSINSTANCES = "processInstances",
      TAG_VALUE_CORESTATISTICS = "corestatistics",
      TAG_VALUE_SUCCEEDED = "succeeded",
      TAG_VALUE_FAILED = "failed";
  private static final Logger LOGGER = LoggerFactory.getLogger(Metrics.class);
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
    registry.counter(TASKLIST_NAMESPACE + name, tags).increment(count);
  }

  public Timer getTimer(final String name, final String... tags) {
    return registry.timer(name, tags);
  }

  public <T> Gauge registerGauge(
      final String name,
      final T stateObject,
      final ToDoubleFunction<T> valueFunction,
      final String... tags) {
    return Gauge.builder(name, () -> valueFunction.applyAsDouble(stateObject))
        .tags(tags)
        .register(registry);
  }

  public Gauge getGauge(final String name, final String... tags) {
    return registry.get(name).tags(tags).gauge();
  }
}

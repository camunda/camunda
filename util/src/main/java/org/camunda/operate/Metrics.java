/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class Metrics {

  private static final Logger logger = LoggerFactory.getLogger(Metrics.class);

  // Namespace (prefix) for operate metrics
  public static final String OPERATE_NAMESPACE = "operate.";

  // Timers:
  public static final String TIMER_NAME_QUERY = OPERATE_NAMESPACE+"query";
  // Counters:
  public static final String COUNTER_NAME_EVENTS_PROCESSED = "events.processed";
  public static final String COUNTER_NAME_EVENTS_PROCESSED_FINISHED_WI = "events.processed.finished.wi";
  public static final String COUNTER_NAME_COMMANDS = "commands";
  public static final String COUNTER_NAME_ARCHIVED = "archived.wi";
  // Tags
  // -----
  //  Keys:
  public static final String TAG_KEY_NAME = "name",
                             TAG_KEY_TYPE = "type",
                             TAG_KEY_STATUS = "status";
  //  Values:
  public static final String TAG_VALUE_WORKFLOWINSTANCES = "workflowInstances",
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
  public void recordCounts(String name, int count, String ... tags) {
    registry.counter(OPERATE_NAMESPACE + name, tags).increment(count);
  }
 
}

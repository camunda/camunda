/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.opentelemetry.api.common.AttributeKey;

/**
 * OTel attribute keys and event name constants for analytics events, grouped by domain. Naming
 * follows OTel semantic conventions: dot-delimited namespaces, snake_case for multi-word
 * components.
 *
 * <p>This class is the single source of truth for all attribute keys. Every attribute key emitted
 * by any handler must be declared here — this invariant is enforced by {@code HandlerPiiGuardTest}.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/general/naming/">OTel Naming</a>
 */
public final class AnalyticsAttributes {

  public static final class Resource {
    public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    public static final AttributeKey<String> CLUSTER_ID =
        AttributeKey.stringKey("camunda.cluster.id");
    public static final AttributeKey<Long> PARTITION_ID =
        AttributeKey.longKey("camunda.partition.id");

    private Resource() {}
  }

  public static final class Event {
    /** OTel semantic convention for events (until Event API is stable). */
    public static final AttributeKey<String> NAME = AttributeKey.stringKey("event.name");

    public static final AttributeKey<Long> SEQUENCE_NUMBER =
        AttributeKey.longKey("camunda.event.sequence_number");
    public static final AttributeKey<Long> TIME_MIN =
        AttributeKey.longKey("camunda.event.time_min");
    public static final AttributeKey<Long> TIME_MAX =
        AttributeKey.longKey("camunda.event.time_max");

    // Event name values
    public static final String PROCESS_INSTANCE_CREATED = "process_instance_created";
    public static final String ADHOC_SUBPROCESS_ACTIVATED = "adhoc_subprocess_activated";
    public static final String USAGE_METRIC_EXPORTED = "usage_metric_exported";
    public static final String HEARTBEAT = "heartbeat";
    public static final String USER_TASK_CREATED = "user_task_created";

    private Event() {}
  }

  public static final class Log {
    public static final AttributeKey<Long> POSITION = AttributeKey.longKey("camunda.log.position");
    public static final AttributeKey<Long> POSITION_START =
        AttributeKey.longKey("camunda.log.position_start");
    public static final AttributeKey<Long> POSITION_END =
        AttributeKey.longKey("camunda.log.position_end");

    private Log() {}
  }

  public static final class Tenant {
    public static final AttributeKey<String> ID = AttributeKey.stringKey("camunda.tenant.id");

    private Tenant() {}
  }

  public static final class Process {
    public static final AttributeKey<String> BPMN_PROCESS_ID =
        AttributeKey.stringKey("camunda.process.id");
    public static final AttributeKey<Long> VERSION =
        AttributeKey.longKey("camunda.process.version");
    public static final AttributeKey<Long> DEFINITION_KEY =
        AttributeKey.longKey("camunda.process.definition_key");
    public static final AttributeKey<Long> INSTANCE_KEY =
        AttributeKey.longKey("camunda.process.instance_key");
    public static final AttributeKey<Long> ROOT_INSTANCE_KEY =
        AttributeKey.longKey("camunda.process.root_instance_key");

    private Process() {}
  }

  public static final class Element {
    public static final AttributeKey<String> ID = AttributeKey.stringKey("camunda.element.id");

    private Element() {}
  }

  public static final class Metric {
    public static final String PROCESS_INSTANCE_CREATED = "camunda.process_instance.created";
    public static final String EXPORT_WINDOW = "camunda.metric.export_window";
    public static final AttributeKey<Long> SEQUENCE_NUMBER =
        AttributeKey.longKey("camunda.metric.sequence_number");

    private Metric() {}
  }

  public static final class Heartbeat {
    public static final AttributeKey<String> BROKER_VERSION =
        AttributeKey.stringKey("camunda.heartbeat.broker_version");
    public static final AttributeKey<String> EXPORTER_VERSION =
        AttributeKey.stringKey("camunda.heartbeat.exporter_version");

    private Heartbeat() {}
  }

  public static final class UsageMetric {
    public static final AttributeKey<String> EVENT_TYPE =
        AttributeKey.stringKey("camunda.usage_metric.event_type");
    public static final AttributeKey<Long> COUNT =
        AttributeKey.longKey("camunda.usage_metric.count");
    public static final AttributeKey<Long> INTERVAL_START =
        AttributeKey.longKey("camunda.usage_metric.interval_start");
    public static final AttributeKey<Long> INTERVAL_END =
        AttributeKey.longKey("camunda.usage_metric.interval_end");

    private UsageMetric() {}
  }

  private AnalyticsAttributes() {}
}

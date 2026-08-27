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
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/general/naming/">OTel Naming</a>
 */
public final class AnalyticsAttributes {

  public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
  public static final AttributeKey<String> CLUSTER_ID =
      AttributeKey.stringKey("camunda.cluster.id");
  public static final AttributeKey<Long> PARTITION_ID =
      AttributeKey.longKey("camunda.partition.id");

  private AnalyticsAttributes() {}

  public static final class Event {
    /** OTel semantic convention for events (until Event API is stable). */
    public static final AttributeKey<String> NAME = AttributeKey.stringKey("event.name");

    public static final AttributeKey<Long> SEQUENCE_NUMBER =
        AttributeKey.longKey("camunda.event.sequence_number");
    public static final AttributeKey<Double> SAMPLE_RATE =
        AttributeKey.doubleKey("camunda.event.sample_rate");
    public static final AttributeKey<Long> TIME_MIN =
        AttributeKey.longKey("camunda.event.time_min");
    public static final AttributeKey<Long> TIME_MAX =
        AttributeKey.longKey("camunda.event.time_max");

    // Event name values

    public static final String PROCESS_INSTANCE_ACTIVATED = "camunda.process.instance.activated";
    public static final String HEARTBEAT = "heartbeat";
    public static final String USER_TASK_CREATED = "user_task_created";
    public static final String TENANT_CREATED = "camunda.tenant.created";
    public static final String TENANT_DELETED = "camunda.tenant.deleted";
    public static final String PROCESS_INCIDENT_CREATED = "camunda.process.incident.created";
    public static final String PROCESS_INCIDENT_RESOLVED = "camunda.process.incident.resolved";
    public static final String PROCESS_DEFINITION_CREATED = "camunda.process.definition.created";
    public static final String PROCESS_DEFINITION_DELETED = "camunda.process.definition.deleted";
    public static final String DECISION_DEFINITION_CREATED = "camunda.decision.definition.created";
    public static final String DECISION_DEFINITION_DELETED = "camunda.decision.definition.deleted";
    public static final String FORM_DEFINITION_CREATED = "camunda.form.definition.created";
    public static final String FORM_DEFINITION_DELETED = "camunda.form.definition.deleted";
    public static final String AGENT_INSTANCE_CREATED = "camunda.agent.instance.created";
    public static final String AGENT_INSTANCE_COMPLETED = "camunda.agent.instance.completed";
    public static final String USER_TASK_ASSIGNED = "camunda.user_task.assigned";

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

    /**
     * Physical-tenant id of the broker/exporter instance that produced the signal. Unlike {@link
     * #ID} (the logical tenant of the specific record, which varies per event), this is static for
     * the lifetime of the exporter instance, so it is set once as an OTel {@code Resource}
     * attribute in {@link io.camunda.exporter.analytics.OtelSdkManager#buildResource} and applies
     * automatically to every log record, metric point, and heartbeat emitted through that Resource
     * — individual handlers and call sites never set it.
     */
    public static final AttributeKey<String> PHYSICAL_ID =
        AttributeKey.stringKey("camunda.tenant.physical_id");

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

  public static final class UserTask {
    public static final AttributeKey<Long> KEY = AttributeKey.longKey("camunda.user_task.key");

    private UserTask() {}
  }

  public static final class Incident {
    public static final AttributeKey<Long> KEY = AttributeKey.longKey("camunda.incident.key");

    private Incident() {}
  }

  public static final class Decision {
    public static final AttributeKey<String> ID = AttributeKey.stringKey("camunda.decision.id");
    public static final AttributeKey<Long> KEY = AttributeKey.longKey("camunda.decision.key");
    public static final AttributeKey<Long> VERSION =
        AttributeKey.longKey("camunda.decision.version");

    private Decision() {}
  }

  public static final class Form {
    public static final AttributeKey<String> ID = AttributeKey.stringKey("camunda.form.id");
    public static final AttributeKey<Long> KEY = AttributeKey.longKey("camunda.form.key");
    public static final AttributeKey<Long> VERSION = AttributeKey.longKey("camunda.form.version");

    private Form() {}
  }

  public static final class Agent {
    public static final AttributeKey<Long> INSTANCE_KEY =
        AttributeKey.longKey("camunda.agent.instance_key");
    public static final AttributeKey<Long> DEFINITION_KEY =
        AttributeKey.longKey("camunda.agent.definition_key");
    public static final AttributeKey<String> STATUS =
        AttributeKey.stringKey("camunda.agent.status");

    private Agent() {}
  }

  public static final class Metric {
    public static final String DECISION_INSTANCE_EVALUATED = "camunda.decision.instance.evaluated";
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

  public static final class Exporter {
    public static final AttributeKey<String> DIGEST =
        AttributeKey.stringKey("camunda.exporter.digest");

    private Exporter() {}
  }
}

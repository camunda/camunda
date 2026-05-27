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
 * OTel attribute keys for analytics events. Naming follows OTel semantic conventions: dot-delimited
 * namespaces, snake_case for multi-word components.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/semconv/general/naming/">OTel Naming</a>
 */
public final class AnalyticsAttributes {

  // Resource-level
  public static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
  public static final AttributeKey<String> CLUSTER_ID =
      AttributeKey.stringKey("camunda.cluster.id");
  public static final AttributeKey<Long> PARTITION_ID =
      AttributeKey.longKey("camunda.partition.id");

  // OTel semantic convention for events (until Event API is stable)
  public static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

  // Log domain
  public static final AttributeKey<Long> LOG_POSITION =
      AttributeKey.longKey("camunda.log.position");
  public static final AttributeKey<Long> LOG_SEQUENCE_NUMBER =
      AttributeKey.longKey("camunda.log.sequence_number");

  // Tenant domain
  public static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("camunda.tenant.id");

  // Process domain
  public static final AttributeKey<String> BPMN_PROCESS_ID =
      AttributeKey.stringKey("camunda.process.id");
  public static final AttributeKey<Long> PROCESS_VERSION =
      AttributeKey.longKey("camunda.process.version");
  public static final AttributeKey<Long> PROCESS_DEFINITION_KEY =
      AttributeKey.longKey("camunda.process.definition_key");
  public static final AttributeKey<Long> PROCESS_INSTANCE_KEY =
      AttributeKey.longKey("camunda.process.instance_key");
  public static final AttributeKey<Long> ROOT_PROCESS_INSTANCE_KEY =
      AttributeKey.longKey("camunda.process.root_instance_key");

  // Element domain
  public static final AttributeKey<String> ELEMENT_ID =
      AttributeKey.stringKey("camunda.element.id");

  // Event names
  public static final String EVENT_PROCESS_INSTANCE_CREATED = "process_instance_created";
  public static final String EVENT_ADHOC_SUBPROCESS_ACTIVATED = "adhoc_subprocess_activated";
  public static final String EVENT_USAGE_METRIC_EXPORTED = "usage_metric_exported";

  // Usage metrics
  public static final AttributeKey<String> USAGE_METRIC_EVENT_TYPE =
      AttributeKey.stringKey("camunda.usage_metric.event_type");
  public static final AttributeKey<Long> USAGE_METRIC_COUNT =
      AttributeKey.longKey("camunda.usage_metric.count");
  public static final AttributeKey<Long> USAGE_METRIC_INTERVAL_START =
      AttributeKey.longKey("camunda.usage_metric.interval_start");
  public static final AttributeKey<Long> USAGE_METRIC_INTERVAL_END =
      AttributeKey.longKey("camunda.usage_metric.interval_end");

  private AnalyticsAttributes() {}
}

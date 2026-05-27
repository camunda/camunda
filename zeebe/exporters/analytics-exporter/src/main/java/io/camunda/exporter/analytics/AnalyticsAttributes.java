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

  // Common
  public static final AttributeKey<Long> LOG_POSITION =
      AttributeKey.longKey("camunda.log.position");
  public static final AttributeKey<Long> SEQUENCE_NUMBER =
      AttributeKey.longKey("camunda.sequence_number");
  public static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("camunda.tenant_id");

  // Process instance
  public static final AttributeKey<String> BPMN_PROCESS_ID =
      AttributeKey.stringKey("camunda.bpmn_process_id");
  public static final AttributeKey<Long> PROCESS_VERSION =
      AttributeKey.longKey("camunda.process_version");
  public static final AttributeKey<Long> PROCESS_DEFINITION_KEY =
      AttributeKey.longKey("camunda.process_definition_key");
  public static final AttributeKey<Long> PROCESS_INSTANCE_KEY =
      AttributeKey.longKey("camunda.process_instance_key");
  public static final AttributeKey<Long> ROOT_PROCESS_INSTANCE_KEY =
      AttributeKey.longKey("camunda.root_process_instance_key");

  // Adhoc subprocess
  public static final AttributeKey<String> ELEMENT_ID =
      AttributeKey.stringKey("camunda.element_id");

  private AnalyticsAttributes() {}
}

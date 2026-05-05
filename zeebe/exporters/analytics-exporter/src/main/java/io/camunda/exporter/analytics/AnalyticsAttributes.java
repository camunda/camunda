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
final class AnalyticsAttributes {

  // Resource-level
  static final AttributeKey<String> CLUSTER_ID = AttributeKey.stringKey("camunda.cluster.id");
  static final AttributeKey<Long> PARTITION_ID = AttributeKey.longKey("camunda.partition.id");

  // OTel semantic convention for events (until Event API is stable)
  static final AttributeKey<String> EVENT_NAME = AttributeKey.stringKey("event.name");

  // Common
  static final AttributeKey<Long> LOG_POSITION = AttributeKey.longKey("camunda.log.position");
  static final AttributeKey<String> TENANT_ID = AttributeKey.stringKey("camunda.tenant_id");

  // Process instance
  static final AttributeKey<String> BPMN_PROCESS_ID =
      AttributeKey.stringKey("camunda.bpmn_process_id");
  static final AttributeKey<Long> PROCESS_VERSION = AttributeKey.longKey("camunda.process_version");
  static final AttributeKey<Long> PROCESS_DEFINITION_KEY =
      AttributeKey.longKey("camunda.process_definition_key");
  static final AttributeKey<Long> PROCESS_INSTANCE_KEY =
      AttributeKey.longKey("camunda.process_instance_key");
  static final AttributeKey<Long> ROOT_PROCESS_INSTANCE_KEY =
      AttributeKey.longKey("camunda.root_process_instance_key");

  private AnalyticsAttributes() {}
}

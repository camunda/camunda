/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.kafka.config;

import java.util.Map;

public record ProducerConfiguration(
    String servers,
    String clientId,
    long requestTimeoutMs,
    long maxBlockingTimeoutMs,
    long closeTimeoutMs,
    Map<String, String> additionalConfig) {}

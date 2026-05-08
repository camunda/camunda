/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobWorker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles the {@code exporter-calculation} job type emitted by the exporter operation BPMN process.
 * Computes a flat list of {@code {memberId, partitionId}} pairs covering every partition on every
 * member in the current cluster, and returns both the operations list and the initial {@code
 * configuration} variable needed by downstream {@code config-change-*} jobs.
 */
public final class ExporterCalculationJobWorker implements AutoCloseable {

  static final String JOB_TYPE = "exporter-calculation";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final CamundaClient camundaClient;
  private JobWorker worker;

  public ExporterCalculationJobWorker(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  @SuppressWarnings("unchecked")
  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final var vars = job.getVariablesAsMap();
    try {
      final var configMap = toConfigMap(vars.get("configuration"));
      final var config = BpmnClusterConfigurationMapper.fromMap(configMap);

      final List<Map<String, Object>> operations = new ArrayList<>();
      for (final var memberEntry : config.members().entrySet()) {
        final var memberId = memberEntry.getKey().id();
        for (final var partitionId : memberEntry.getValue().partitions().keySet()) {
          operations.add(Map.of("memberId", memberId, "partitionId", partitionId));
        }
      }

      final Map<String, Object> result = new java.util.HashMap<>();
      result.put("operations", operations);
      jobClient.newCompleteCommand(job.getKey()).variables(result).send();

    } catch (final Exception e) {
      jobClient
          .newThrowErrorCommand(job.getKey())
          .errorCode("EXPORTER_CALCULATION_FAILED")
          .errorMessage(e.getMessage())
          .send();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toConfigMap(final Object raw) throws IOException {
    if (raw instanceof final Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    if (raw instanceof final String json) {
      return OBJECT_MAPPER.readValue(json, MAP_TYPE);
    }
    throw new IllegalArgumentException(
        "currentClusterConfiguration must be a JSON object or string, got: "
            + (raw == null ? "null" : raw.getClass().getName()));
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }
}

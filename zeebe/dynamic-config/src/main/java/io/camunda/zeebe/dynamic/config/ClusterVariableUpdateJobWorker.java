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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the {@code update-cluster-variable} job type emitted at the end of the scale operation
 * BPMN process. Reads the final {@code configuration} variable and writes it to the global Cluster
 * Variable {@code clusterConfiguration}, keeping it in sync with the actual topology after every
 * scale operation.
 *
 * <p>Uses PUT (update) as the primary path, falling back to POST (create) on 404 so the worker is
 * robust against a missing variable (e.g. the bootstrap create failed transiently).
 */
public final class ClusterVariableUpdateJobWorker implements AutoCloseable {

  static final String JOB_TYPE = "update-cluster-variable";
  static final String VARIABLE_NAME = "clusterConfiguration";

  private static final Logger LOG = LoggerFactory.getLogger(ClusterVariableUpdateJobWorker.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final CamundaClient camundaClient;
  private JobWorker worker;

  public ClusterVariableUpdateJobWorker(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void start() {
    worker = camundaClient.newWorker().jobType(JOB_TYPE).handler(this::handleJob).open();
  }

  @SuppressWarnings("unchecked")
  private void handleJob(final JobClient jobClient, final ActivatedJob job) {
    final var vars = job.getVariablesAsMap();
    try {
      final Map<String, Object> configMap = toConfigMap(vars.get("configuration"));

      camundaClient
          .newGloballyScopedClusterVariableUpdateRequest()
          .update(VARIABLE_NAME, configMap)
          .send()
          .whenComplete(
              (updateResult, updateErr) -> {
                if (updateErr == null) {
                  jobClient.newCompleteCommand(job.getKey()).send();
                } else {
                  final String msg = updateErr.getMessage();
                  if (msg != null && (msg.contains("404") || msg.contains("NOT_FOUND"))) {
                    // Variable doesn't exist yet (bootstrap failed) — fall back to create
                    LOG.debug("PUT clusterConfiguration got 404, falling back to create");
                    camundaClient
                        .newGloballyScopedClusterVariableCreateRequest()
                        .create(VARIABLE_NAME, configMap)
                        .send()
                        .whenComplete(
                            (createResult, createErr) -> {
                              if (createErr == null) {
                                jobClient.newCompleteCommand(job.getKey()).send();
                              } else {
                                jobClient
                                    .newFailCommand(job.getKey())
                                    .retries(job.getRetries() - 1)
                                    .errorMessage(createErr.getMessage())
                                    .send();
                              }
                            });
                  } else {
                    // Transient failure (e.g. partition leadership transition) — retry
                    LOG.debug("PUT clusterConfiguration failed transiently ({}), retrying", msg);
                    jobClient
                        .newFailCommand(job.getKey())
                        .retries(job.getRetries() - 1)
                        .errorMessage(msg)
                        .send();
                  }
                }
              });
    } catch (final Exception e) {
      jobClient
          .newFailCommand(job.getKey())
          .retries(job.getRetries() - 1)
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
        "configuration must be a JSON object or string, got: "
            + (raw == null ? "null" : raw.getClass().getName()));
  }

  @Override
  public void close() {
    if (worker != null) {
      worker.close();
    }
  }
}

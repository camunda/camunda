/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.worker;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Exercises the {@code /v2/secrets/list} endpoint once per process instance of the
 * connectorSecretResolution benchmark (see connectorSecretResolution.bpmn), independently of the
 * resolveSecret branch: the two run off a parallel gateway, so list traffic neither delays nor is
 * delayed by the connector's own resolve traffic.
 *
 * <p>Fixed job type, not driven by {@code load-tester.worker.*}: unlike the generic {@link Worker},
 * which completes every job with a static payload, this worker always issues a real {@link
 * CamundaClient#newListSecretsCommand()} call, so its job type is intentionally hardcoded to match
 * the one BPMN model that needs it rather than being reconfigurable per deployment.
 */
@Component
@Profile("worker")
public class ListSecretsWorker {

  static final String JOB_TYPE = "benchmark-list-secrets";

  private static final Logger LOGGER = LoggerFactory.getLogger(ListSecretsWorker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));

  private final CamundaClient client;

  public ListSecretsWorker(final CamundaClient client) {
    this.client = client;
  }

  @JobWorker(type = JOB_TYPE, autoComplete = false)
  public void handleJob(final JobClient jobClient, final ActivatedJob job) {
    client
        .newListSecretsCommand()
        .send()
        .whenComplete(
            (response, error) -> {
              if (error != null) {
                // Same policy as Worker#publishMessage: let the job time out instead of failing
                // it, so a transient list-endpoint error delays this one instance rather than
                // permanently failing it or spamming retries.
                THROTTLED_LOGGER.warn(
                    "Failed to list secrets for job {}: {}",
                    job.getKey(),
                    error.getClass().getName());
                return;
              }
              jobClient
                  .newCompleteCommand(job.getKey())
                  .variables(Map.of("listedSecretCount", response.getReferences().size()))
                  .send()
                  .exceptionally(
                      completeError -> {
                        THROTTLED_LOGGER.warn(
                            "Failed to complete list-secrets job {}: {}",
                            job.getKey(),
                            completeError.getClass().getName());
                        return null;
                      });
            });
  }
}

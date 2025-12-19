/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.ProcessInstanceStartMeter;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstanceUtil {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessInstanceUtil.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<HashMap<String, Object>> VARIABLES_TYPE_REF =
      new TypeReference<>() {};

  public static CompletionStage<ProcessInstanceEvent> startInstance(
      final CamundaClient client,
      final String processId,
      final Map<String, Object> variables,
      final ProcessInstanceStartMeter processInstanceStartMeter) {
    final var startTime = System.nanoTime();
    final var sendFuture =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .variables(variables)
            .send();

    if (processInstanceStartMeter != null) {
      return sendFuture.thenApply(
          (response) -> {
            final long processInstanceKey = response.getProcessInstanceKey();
            processInstanceStartMeter.recordProcessInstanceStart(processInstanceKey, startTime);
            return response;
          });
    }

    return sendFuture;
  }

  public static CamundaFuture<ProcessInstanceResult> startInstanceWithAwaitingResult(
      final CamundaClient client,
      final String processId,
      final HashMap<String, Object> variables,
      final Duration timeout) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processId)
        .latestVersion()
        .variables(variables)
        .withResult()
        .requestTimeout(timeout)
        .send();
  }

  public static CamundaFuture<PublishMessageResponse> startInstanceByMessagePublishing(
      final CamundaClient client, final Map<String, Object> variables, final String messageName) {
    return client
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(UUID.randomUUID().toString())
        .variables(variables)
        .timeToLive(Duration.ZERO)
        .send();
  }

  public static HashMap<String, Object> deserializeVariables(final String variablesString) {
    final HashMap<String, Object> variables;
    try {
      variables = OBJECT_MAPPER.readValue(variablesString, VARIABLES_TYPE_REF);
    } catch (final JsonProcessingException e) {
      LOG.error("Failed to parse variables '{}'.", variablesString, e);
      throw new RuntimeException(e);
    }
    return variables;
  }
}

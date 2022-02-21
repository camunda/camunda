/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class TaskHeadersTransformer {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  public void transform(
      final ExecutableJobWorkerElement element,
      final ZeebeTaskHeaders taskHeaders,
      final FlowElement task) {

    if (taskHeaders == null) {
      return;
    }

    final var jobWorkerProperties =
        Optional.ofNullable(element.getJobWorkerProperties()).orElse(new JobWorkerProperties());
    element.setJobWorkerProperties(jobWorkerProperties);

    final Map<String, String> validHeaders =
        taskHeaders.getHeaders().stream()
            .filter(this::isValidHeader)
            .collect(Collectors.toMap(ZeebeHeader::getKey, ZeebeHeader::getValue));

    if (validHeaders.size() < taskHeaders.getHeaders().size()) {
      LOG.warn(
          "Ignoring invalid headers for task '{}'. Must have non-empty key and value.",
          task.getName());
    }

    if (!validHeaders.isEmpty()) {
      jobWorkerProperties.setTaskHeaders(validHeaders);
    }
  }

  public boolean isValidHeader(final ZeebeHeader header) {
    return header != null
        && header.getValue() != null
        && !header.getValue().isEmpty()
        && header.getKey() != null
        && !header.getKey().isEmpty();
  }
}

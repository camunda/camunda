/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class JobWorkerElementTransformer<T extends FlowElement>
    implements ModelElementTransformer<T> {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final Class<T> type;

  public JobWorkerElementTransformer(final Class<T> type) {
    this.type = type;
  }

  @Override
  public Class<T> getType() {
    return type;
  }

  @Override
  public void transform(final T element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableJobWorkerElement jobWorkerElement =
        process.getElementById(element.getId(), ExecutableJobWorkerElement.class);

    final var jobWorkerProperties = new JobWorkerProperties();
    jobWorkerElement.setJobWorkerProperties(jobWorkerProperties);

    transformTaskDefinition(element, jobWorkerProperties, context);
    transformTaskHeaders(element, jobWorkerProperties);
  }

  private void transformTaskDefinition(
      final T element,
      final JobWorkerProperties jobWorkerProperties,
      final TransformContext context) {
    final ZeebeTaskDefinition taskDefinition =
        element.getSingleExtensionElement(ZeebeTaskDefinition.class);

    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final Expression jobTypeExpression =
        expressionLanguage.parseExpression(taskDefinition.getType());

    jobWorkerProperties.setType(jobTypeExpression);

    final Expression retriesExpression =
        expressionLanguage.parseExpression(taskDefinition.getRetries());

    jobWorkerProperties.setRetries(retriesExpression);
  }

  private void transformTaskHeaders(
      final T element, final JobWorkerProperties jobWorkerProperties) {
    final ZeebeTaskHeaders taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);

    if (taskHeaders != null) {
      final Map<String, String> validHeaders =
          taskHeaders.getHeaders().stream()
              .filter(this::isValidHeader)
              .collect(Collectors.toMap(ZeebeHeader::getKey, ZeebeHeader::getValue));

      if (validHeaders.size() < taskHeaders.getHeaders().size()) {
        LOG.warn(
            "Ignoring invalid headers for task '{}'. Must have non-empty key and value.",
            element.getName());
      }

      if (!validHeaders.isEmpty()) {
        jobWorkerProperties.setTaskHeaders(validHeaders);
      }
    }
  }

  private boolean isValidHeader(final ZeebeHeader header) {
    return header != null
        && header.getValue() != null
        && !header.getValue().isEmpty()
        && header.getKey() != null
        && !header.getKey().isEmpty();
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

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
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class JobWorkerElementTransformer<T extends FlowElement>
    implements ModelElementTransformer<T> {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();

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
      final List<ZeebeHeader> validHeaders =
          taskHeaders.getHeaders().stream()
              .filter(this::isValidHeader)
              .collect(Collectors.toList());

      if (validHeaders.size() < taskHeaders.getHeaders().size()) {
        LOG.warn(
            "Ignoring invalid headers for task '{}'. Must have non-empty key and value.",
            element.getName());
      }

      if (!validHeaders.isEmpty()) {
        final DirectBuffer encodedHeaders = encode(validHeaders);
        jobWorkerProperties.setEncodedHeaders(encodedHeaders);
      }
    }
  }

  private DirectBuffer encode(final List<ZeebeHeader> taskHeaders) {
    final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);

    final ExpandableArrayBuffer expandableBuffer =
        new ExpandableArrayBuffer(INITIAL_SIZE_KEY_VALUE_PAIR * taskHeaders.size());

    msgPackWriter.wrap(expandableBuffer, 0);
    msgPackWriter.writeMapHeader(taskHeaders.size());

    taskHeaders.forEach(
        h -> {
          if (isValidHeader(h)) {
            final DirectBuffer key = wrapString(h.getKey());
            msgPackWriter.writeString(key);

            final DirectBuffer value = wrapString(h.getValue());
            msgPackWriter.writeString(value);
          }
        });

    buffer.wrap(expandableBuffer.byteArray(), 0, msgPackWriter.getOffset());

    return buffer;
  }

  private boolean isValidHeader(final ZeebeHeader header) {
    return header != null
        && header.getValue() != null
        && !header.getValue().isEmpty()
        && header.getKey() != null
        && !header.getKey().isEmpty();
  }
}

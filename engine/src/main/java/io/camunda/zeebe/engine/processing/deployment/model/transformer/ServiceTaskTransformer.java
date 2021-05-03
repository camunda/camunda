/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.zeebe.msgpack.spec.MsgPackWriter;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class ServiceTaskTransformer implements ModelElementTransformer<ServiceTask> {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();

  @Override
  public Class<ServiceTask> getType() {
    return ServiceTask.class;
  }

  @Override
  public void transform(final ServiceTask element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableServiceTask serviceTask =
        process.getElementById(element.getId(), ExecutableServiceTask.class);

    transformTaskDefinition(element, serviceTask, context);

    transformTaskHeaders(element, serviceTask);
  }

  private void transformTaskDefinition(
      final ServiceTask element,
      final ExecutableServiceTask serviceTask,
      final TransformContext context) {
    final ZeebeTaskDefinition taskDefinition =
        element.getSingleExtensionElement(ZeebeTaskDefinition.class);

    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final Expression jobTypeExpression =
        expressionLanguage.parseExpression(taskDefinition.getType());

    serviceTask.setType(jobTypeExpression);

    final Expression retriesExpression =
        expressionLanguage.parseExpression(taskDefinition.getRetries());

    serviceTask.setRetries(retriesExpression);
  }

  private void transformTaskHeaders(
      final ServiceTask element, final ExecutableServiceTask serviceTask) {
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
        serviceTask.setEncodedHeaders(encodedHeaders);
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

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.el.impl.StaticExpression;
import io.zeebe.engine.Loggers;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.element.ExecutableServiceTask;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.UserTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;
import io.zeebe.msgpack.spec.MsgPackWriter;
import io.zeebe.protocol.Protocol;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public final class UserTaskTransformer implements ModelElementTransformer<UserTask> {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private static final int INITIAL_SIZE_KEY_VALUE_PAIR = 128;

  private final MsgPackWriter msgPackWriter = new MsgPackWriter();

  @Override
  public Class<UserTask> getType() {
    return UserTask.class;
  }

  @Override
  public void transform(final UserTask element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableServiceTask userTask =
        process.getElementById(element.getId(), ExecutableServiceTask.class);

    transformTaskDefinition(element, userTask, context);

    transformTaskHeaders(element, userTask);
  }

  private void transformTaskDefinition(
      final UserTask element,
      final ExecutableServiceTask userTask,
      final TransformContext context) {
    userTask.setType(new StaticExpression(Protocol.USER_TASK_JOB_TYPE));
    userTask.setRetries(new StaticExpression("1"));
  }

  private void transformTaskHeaders(final UserTask element, final ExecutableServiceTask userTask) {
    final ZeebeTaskHeaders taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);

    addZeebeUserTaskFormKeyHeader(element, taskHeaders);

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
        userTask.setEncodedHeaders(encodedHeaders);
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

  private void addZeebeUserTaskFormKeyHeader(
      final UserTask element, final ZeebeTaskHeaders taskHeaders) {
    final ZeebeFormDefinition formDefinition =
        element.getSingleExtensionElement(ZeebeFormDefinition.class);

    if (formDefinition != null) {
      final ZeebeHeader zeebeHeader = element.getModelInstance().newInstance(ZeebeHeader.class);
      zeebeHeader.setKey(Protocol.USER_TASK_FORM_KEY_HEADER_NAME);
      zeebeHeader.setValue(formDefinition.getFormKey());
      taskHeaders.getHeaders().add(zeebeHeader);
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

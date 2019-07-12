/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.util.buffer.BufferUtil;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;

/** Executable* prefix in order to avoid confusion with model API classes. */
public class ExecutableWorkflow extends ExecutableFlowElementContainer {

  private final Map<DirectBuffer, AbstractFlowElement> flowElements = new HashMap<>();

  public ExecutableWorkflow(String id) {
    super(id);
    addFlowElement(this);
  }

  public void addFlowElement(AbstractFlowElement element) {
    flowElements.put(element.getId(), element);
  }

  public AbstractFlowElement getElementById(DirectBuffer id) {
    return flowElements.get(id);
  }

  /** convenience function for transformation */
  public <T extends ExecutableFlowElement> T getElementById(String id, Class<T> expectedType) {
    final DirectBuffer buffer = BufferUtil.wrapString(id);
    final ExecutableFlowElement element = flowElements.get(buffer);
    if (element == null) {
      return null;
    }

    if (expectedType.isAssignableFrom(element.getClass())) {
      return (T) element;
    } else {
      throw new RuntimeException(
          String.format(
              "Expected element with id '%s' to be instance of class '%s', but it is an instance of '%s'",
              id, expectedType.getSimpleName(), element.getClass().getSimpleName()));
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformation;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public class TransformContext {

  private final Map<DirectBuffer, ExecutableWorkflow> workflows = new HashMap<>();
  private final Map<DirectBuffer, ExecutableMessage> messages = new HashMap<>();
  private JsonPathQueryCompiler jsonPathQueryCompiler;

  /*
   * set whenever parsing a workflow
   */
  private ExecutableWorkflow currentWorkflow;

  public ExecutableWorkflow getCurrentWorkflow() {
    return currentWorkflow;
  }

  public void setCurrentWorkflow(ExecutableWorkflow currentWorkflow) {
    this.currentWorkflow = currentWorkflow;
  }

  public void addWorkflow(ExecutableWorkflow workflow) {
    workflows.put(workflow.getId(), workflow);
  }

  public ExecutableWorkflow getWorkflow(String id) {
    return workflows.get(BufferUtil.wrapString(id));
  }

  public List<ExecutableWorkflow> getWorkflows() {
    return new ArrayList<>(workflows.values());
  }

  public void addMessage(ExecutableMessage message) {
    messages.put(message.getId(), message);
  }

  public ExecutableMessage getMessage(String id) {
    return messages.get(BufferUtil.wrapString(id));
  }

  public JsonPathQueryCompiler getJsonPathQueryCompiler() {
    return jsonPathQueryCompiler;
  }

  public void setJsonPathQueryCompiler(JsonPathQueryCompiler jsonPathQueryCompiler) {
    this.jsonPathQueryCompiler = jsonPathQueryCompiler;
  }
}

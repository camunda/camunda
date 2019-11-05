/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class ExecutableCallActivity extends ExecutableActivity {

  private Optional<DirectBuffer> calledElementProcessId = Optional.empty();
  private Optional<JsonPathQuery> calledElementProcessIdExpression = Optional.empty();

  public ExecutableCallActivity(String id) {
    super(id);
  }

  public Optional<DirectBuffer> getCalledElementProcessId() {
    return calledElementProcessId;
  }

  public void setCalledElementProcessId(String calledElementProcessId) {
    this.calledElementProcessId = Optional.of(BufferUtil.wrapString(calledElementProcessId));
  }

  public Optional<JsonPathQuery> getCalledElementProcessIdExpression() {
    return calledElementProcessIdExpression;
  }

  public void setCalledElementProcessIdExpression(JsonPathQuery calledElementProcessIdExpression) {
    this.calledElementProcessIdExpression = Optional.of(calledElementProcessIdExpression);
  }
}

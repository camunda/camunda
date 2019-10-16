/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ExecutableCallActivity extends ExecutableActivity {

  private DirectBuffer calledElementProcessId;

  public ExecutableCallActivity(String id) {
    super(id);
  }

  public DirectBuffer getCalledElementProcessId() {
    return calledElementProcessId;
  }

  public void setCalledElementProcessId(String calledElementProcessId) {
    this.calledElementProcessId = BufferUtil.wrapString(calledElementProcessId);
  }
}

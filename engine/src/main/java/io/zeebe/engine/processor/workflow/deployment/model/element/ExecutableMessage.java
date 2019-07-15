/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.element;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import org.agrona.DirectBuffer;

public class ExecutableMessage extends AbstractFlowElement {

  private JsonPathQuery correlationKey;
  private DirectBuffer messageName;

  public ExecutableMessage(String id) {
    super(id);
  }

  public JsonPathQuery getCorrelationKey() {
    return correlationKey;
  }

  public void setCorrelationKey(JsonPathQuery correlationKey) {
    this.correlationKey = correlationKey;
  }

  public DirectBuffer getMessageName() {
    return messageName;
  }

  public void setMessageName(DirectBuffer messageName) {
    this.messageName = messageName;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

import io.camunda.microbenchmarks.msgpack.Pojo.POJOEnum;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/** Hand-written {@link UnpackedObject} with the stored shape of {@link PojoBig}. */
public final class UnpackedBig extends UnpackedObject {

  private final LongProperty processInstanceKey = new LongProperty("processInstanceKey");
  private final LongProperty flowScopeKey = new LongProperty("flowScopeKey", -1L);
  private final LongProperty elementInstanceKey = new LongProperty("elementInstanceKey");
  private final LongProperty parentProcessInstanceKey =
      new LongProperty("parentProcessInstanceKey", -1L);
  private final LongProperty parentElementInstanceKey =
      new LongProperty("parentElementInstanceKey", -1L);
  private final LongProperty rootProcessInstanceKey =
      new LongProperty("rootProcessInstanceKey", -1L);
  private final LongProperty processDefinitionKey = new LongProperty("processDefinitionKey");
  private final LongProperty jobKey = new LongProperty("jobKey", -1L);
  private final LongProperty deadline = new LongProperty("deadline", -1L);
  private final IntegerProperty version = new IntegerProperty("version", -1);
  private final IntegerProperty partitionId = new IntegerProperty("partitionId");
  private final IntegerProperty retries = new IntegerProperty("retries", 0);
  private final IntegerProperty loopCounter = new IntegerProperty("loopCounter", 0);
  private final IntegerProperty activeTokens = new IntegerProperty("activeTokens", 0);
  private final BooleanProperty interrupted = new BooleanProperty("interrupted", false);
  private final EnumProperty<POJOEnum> kind =
      new EnumProperty<>("kind", POJOEnum.class, POJOEnum.FOO);
  private final StringProperty processId = new StringProperty("processId", "");
  private final StringProperty elementId = new StringProperty("elementId", "");
  private final StringProperty tenantId = new StringProperty("tenantId", "");
  private final StringProperty errorMessage = new StringProperty("errorMessage", "");

  public UnpackedBig() {
    super(20);
    declareProperty(processInstanceKey)
        .declareProperty(flowScopeKey)
        .declareProperty(elementInstanceKey)
        .declareProperty(parentProcessInstanceKey)
        .declareProperty(parentElementInstanceKey)
        .declareProperty(rootProcessInstanceKey)
        .declareProperty(processDefinitionKey)
        .declareProperty(jobKey)
        .declareProperty(deadline)
        .declareProperty(version)
        .declareProperty(partitionId)
        .declareProperty(retries)
        .declareProperty(loopCounter)
        .declareProperty(activeTokens)
        .declareProperty(interrupted)
        .declareProperty(kind)
        .declareProperty(processId)
        .declareProperty(elementId)
        .declareProperty(tenantId)
        .declareProperty(errorMessage);
  }

  public UnpackedBig fill(final int seed) {
    processInstanceKey.setValue(2251799813685249L + seed);
    flowScopeKey.setValue(2251799813685249L + seed);
    elementInstanceKey.setValue(2251799813685300L + seed);
    processDefinitionKey.setValue(2251799813685100L + seed);
    jobKey.setValue(2251799813685400L + seed);
    version.setValue(3);
    partitionId.setValue(1 + seed % 3);
    processId.setValue(BufferUtil.wrapString("process-" + (seed % 10)));
    elementId.setValue(BufferUtil.wrapString("task-" + (seed % 7)));
    tenantId.setValue(BufferUtil.wrapString("<default>"));
    return this;
  }
}

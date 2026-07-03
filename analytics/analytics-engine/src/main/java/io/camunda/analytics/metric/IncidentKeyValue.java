/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.metric;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;

/** Record flyweight for {@link IncidentKey}, so the durable rollup can persist it as a cell key. */
public final class IncidentKeyValue extends UnpackedObject implements RecordValue<IncidentKey> {

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");

  public IncidentKeyValue() {
    super(3);
    declareProperty(bpmnProcessIdProp).declareProperty(elementIdProp).declareProperty(tenantIdProp);
  }

  @Override
  public IncidentKeyValue wrapValue(final IncidentKey key) {
    bpmnProcessIdProp.setValue(key.bpmnProcessId());
    elementIdProp.setValue(key.elementId());
    tenantIdProp.setValue(key.tenantId());
    return this;
  }

  @Override
  public IncidentKey value() {
    return new IncidentKey(
        bufferAsString(bpmnProcessIdProp.getValue()),
        bufferAsString(elementIdProp.getValue()),
        bufferAsString(tenantIdProp.getValue()));
  }
}

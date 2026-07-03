/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.element;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.eventbridge.streaming.aggregate.RecordValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/**
 * Record flyweight for {@link ElementKey}, so the durable heatmap rollup can persist it as a cell
 * key.
 */
public final class ElementKeyValue extends UnpackedObject implements RecordValue<ElementKey> {

  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", 0L);
  private final IntegerProperty versionProp = new IntegerProperty("version", 0);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final StringProperty elementTypeProp = new StringProperty("elementType", "");

  public ElementKeyValue() {
    super(6);
    declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(versionProp)
        .declareProperty(tenantIdProp)
        .declareProperty(elementIdProp)
        .declareProperty(elementTypeProp);
  }

  @Override
  public ElementKeyValue wrapValue(final ElementKey key) {
    bpmnProcessIdProp.setValue(key.bpmnProcessId());
    processDefinitionKeyProp.setValue(key.processDefinitionKey());
    versionProp.setValue(key.version());
    tenantIdProp.setValue(key.tenantId());
    elementIdProp.setValue(key.elementId());
    elementTypeProp.setValue(key.elementType());
    return this;
  }

  @Override
  public ElementKey value() {
    return new ElementKey(
        bufferAsString(bpmnProcessIdProp.getValue()),
        processDefinitionKeyProp.getValue(),
        versionProp.getValue(),
        bufferAsString(tenantIdProp.getValue()),
        bufferAsString(elementIdProp.getValue()),
        bufferAsString(elementTypeProp.getValue()));
  }
}

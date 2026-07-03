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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/** Record flyweight for {@link RegionKey}, a cell-key component of the durable rollup. */
public final class RegionKeyValue extends UnpackedObject implements RecordValue<RegionKey> {

  private final StringProperty regionProp = new StringProperty("region", "");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", 0L);
  private final IntegerProperty versionProp = new IntegerProperty("version", 0);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");

  public RegionKeyValue() {
    super(5);
    declareProperty(regionProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(versionProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public RegionKeyValue wrapValue(final RegionKey key) {
    regionProp.setValue(key.region());
    bpmnProcessIdProp.setValue(key.bpmnProcessId());
    processDefinitionKeyProp.setValue(key.processDefinitionKey());
    versionProp.setValue(key.version());
    tenantIdProp.setValue(key.tenantId());
    return this;
  }

  @Override
  public RegionKey value() {
    return new RegionKey(
        bufferAsString(regionProp.getValue()),
        bufferAsString(bpmnProcessIdProp.getValue()),
        processDefinitionKeyProp.getValue(),
        versionProp.getValue(),
        bufferAsString(tenantIdProp.getValue()));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.resource;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;

public class ResourceDeletionRecord extends UnifiedRecordValue
    implements ResourceDeletionRecordValue {

  private final LongProperty resourceKeyProp = new LongProperty("resourceKey");

  public ResourceDeletionRecord() {
    declareProperty(resourceKeyProp);
  }

  public void wrap(final ResourceDeletionRecord record) {
    resourceKeyProp.setValue(record.getResourceKey());
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public ResourceDeletionRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }
}

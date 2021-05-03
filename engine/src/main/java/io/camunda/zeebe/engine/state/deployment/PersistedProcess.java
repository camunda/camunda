/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import io.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import org.agrona.DirectBuffer;

public final class PersistedProcess extends UnpackedObject implements DbValue {
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final LongProperty keyProp = new LongProperty("key", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty resourceProp = new BinaryProperty("resource");

  public PersistedProcess() {
    declareProperty(versionProp)
        .declareProperty(keyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(resourceNameProp)
        .declareProperty(resourceProp);
  }

  public void wrap(final ProcessRecord processRecord, final long processDefinitionKey) {
    bpmnProcessIdProp.setValue(processRecord.getBpmnProcessIdBuffer());
    resourceNameProp.setValue(processRecord.getResourceNameBuffer());
    resourceProp.setValue(processRecord.getResourceBuffer());

    versionProp.setValue(processRecord.getVersion());
    keyProp.setValue(processDefinitionKey);
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public long getKey() {
    return keyProp.getValue();
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public DirectBuffer getResource() {
    return resourceProp.getValue();
  }
}

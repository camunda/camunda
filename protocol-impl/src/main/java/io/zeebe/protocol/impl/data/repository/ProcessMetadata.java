/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.protocol.impl.data.repository;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class ProcessMetadata extends UnpackedObject {
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1);
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");

  public ProcessMetadata() {
    declareProperty(processDefinitionKeyProp)
        .declareProperty(versionProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(resourceNameProp);
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ProcessMetadata setProcessDefinitionKey(final long key) {
    processDefinitionKeyProp.setValue(key);
    return this;
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public ProcessMetadata setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public ProcessMetadata setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public ProcessMetadata setBpmnProcessId(final String value) {
    bpmnProcessIdProp.setValue(value);
    return this;
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public ProcessMetadata setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public ProcessMetadata setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }
}

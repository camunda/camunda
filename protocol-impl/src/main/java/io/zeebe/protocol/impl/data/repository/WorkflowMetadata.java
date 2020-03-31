/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl.data.repository;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public class WorkflowMetadata extends UnpackedObject {
  private final LongProperty workflowKeyProp = new LongProperty("workflowKey", -1);
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");

  public WorkflowMetadata() {
    declareProperty(workflowKeyProp)
        .declareProperty(versionProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(resourceNameProp);
  }

  public long getWorkflowKey() {
    return workflowKeyProp.getValue();
  }

  public WorkflowMetadata setWorkflowKey(final long key) {
    workflowKeyProp.setValue(key);
    return this;
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public WorkflowMetadata setVersion(final int version) {
    versionProp.setValue(version);
    return this;
  }

  public DirectBuffer getBpmnProcessId() {
    return bpmnProcessIdProp.getValue();
  }

  public WorkflowMetadata setBpmnProcessId(final DirectBuffer directBuffer) {
    bpmnProcessIdProp.setValue(directBuffer);
    return this;
  }

  public WorkflowMetadata setBpmnProcessId(final String value) {
    bpmnProcessIdProp.setValue(value);
    return this;
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public WorkflowMetadata setResourceName(final DirectBuffer resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public WorkflowMetadata setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import static io.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.readIntoBuffer;
import static io.zeebe.util.buffer.BufferUtil.writeIntoBuffer;

import io.zeebe.db.DbValue;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.zeebe.protocol.impl.record.value.deployment.Workflow;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class PersistedWorkflow implements DbValue {
  final UnsafeBuffer bpmnProcessId = new UnsafeBuffer(0, 0);
  final UnsafeBuffer resourceName = new UnsafeBuffer(0, 0);
  final UnsafeBuffer resource = new UnsafeBuffer(0, 0);
  int version = -1;
  long key = -1;

  public void wrap(DeploymentResource resource, Workflow workflow, long workflowKey) {
    this.resource.wrap(resource.getResourceBuffer());
    this.resourceName.wrap(resource.getResourceNameBuffer());
    this.bpmnProcessId.wrap(workflow.getBpmnProcessIdBuffer());

    this.version = workflow.getVersion();
    this.key = workflowKey;
  }

  public int getVersion() {
    return version;
  }

  public long getKey() {
    return key;
  }

  public UnsafeBuffer getBpmnProcessId() {
    return bpmnProcessId;
  }

  public UnsafeBuffer getResourceName() {
    return resourceName;
  }

  public UnsafeBuffer getResource() {
    return resource;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    int valueOffset = offset;
    version = buffer.getInt(offset, ZB_DB_BYTE_ORDER);
    valueOffset += Integer.BYTES;
    key = buffer.getLong(valueOffset, ZB_DB_BYTE_ORDER);
    valueOffset += Long.BYTES;
    valueOffset = readIntoBuffer(buffer, valueOffset, bpmnProcessId);
    valueOffset = readIntoBuffer(buffer, valueOffset, resourceName);
    readIntoBuffer(buffer, valueOffset, resource);
  }

  @Override
  public int getLength() {
    return Long.BYTES
        + Integer.BYTES
        + Integer.BYTES * 3 // sizes
        + bpmnProcessId.capacity()
        + resourceName.capacity()
        + resource.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    int valueOffset = offset;
    buffer.putInt(offset, version, ZB_DB_BYTE_ORDER);
    valueOffset += Integer.BYTES;
    buffer.putLong(valueOffset, key, ZB_DB_BYTE_ORDER);
    valueOffset += Long.BYTES;
    valueOffset = writeIntoBuffer(buffer, valueOffset, bpmnProcessId);
    valueOffset = writeIntoBuffer(buffer, valueOffset, resourceName);
    valueOffset = writeIntoBuffer(buffer, valueOffset, resource);
    assert (valueOffset - offset) == getLength() : "End offset differs with getLength()";
  }

  @Override
  public String toString() {
    return "PersistedWorkflow{"
        + "version="
        + version
        + ", key="
        + key
        + ", bpmnProcessId="
        + bufferAsString(bpmnProcessId)
        + ", resourceName="
        + bufferAsString(resourceName)
        + ", resource="
        + bufferAsString(resource)
        + '}';
  }
}

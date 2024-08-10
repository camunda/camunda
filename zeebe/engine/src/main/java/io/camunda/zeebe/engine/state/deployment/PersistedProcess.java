/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.agrona.DirectBuffer;

public final class PersistedProcess extends UnpackedObject implements DbValue {
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final LongProperty keyProp = new LongProperty("key", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty resourceProp = new BinaryProperty("resource");
  private final EnumProperty<PersistedProcessState> stateProp =
      new EnumProperty<>("state", PersistedProcessState.class, PersistedProcessState.ACTIVE);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1L);

  public PersistedProcess() {
    super(8);
    declareProperty(versionProp)
        .declareProperty(keyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(resourceNameProp)
        .declareProperty(resourceProp)
        .declareProperty(stateProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp);
  }

  public void wrap(final ProcessRecord processRecord, final long processDefinitionKey) {
    bpmnProcessIdProp.setValue(processRecord.getBpmnProcessIdBuffer());
    resourceNameProp.setValue(processRecord.getResourceNameBuffer());
    resourceProp.setValue(processRecord.getResourceBuffer());

    versionProp.setValue(processRecord.getVersion());
    keyProp.setValue(processDefinitionKey);
    tenantIdProp.setValue(processRecord.getTenantId());
    deploymentKeyProp.setValue(processRecord.getDeploymentKey());
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

  public PersistedProcessState getState() {
    return stateProp.getValue();
  }

  public PersistedProcess setState(final PersistedProcessState state) {
    stateProp.setValue(state);
    return this;
  }

  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public PersistedProcess setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public enum PersistedProcessState {
    ACTIVE(0),
    PENDING_DELETION(1);

    byte value;

    PersistedProcessState(final int value) {
      this.value = (byte) value;
    }
  }
}

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
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class PersistedForm extends UnpackedObject implements DbValue {
  private final StringProperty formIdProp = new StringProperty("formId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty formKeyProp = new LongProperty("formKey");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty resourceProp = new BinaryProperty("resource", new UnsafeBuffer());
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final LongProperty deploymentKeyProp = new LongProperty("deploymentKey", -1L);

  public PersistedForm() {
    super(8);
    declareProperty(formIdProp)
        .declareProperty(versionProp)
        .declareProperty(formKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(resourceProp)
        .declareProperty(checksumProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deploymentKeyProp);
  }

  public PersistedForm copy() {
    final var copy = new PersistedForm();
    copy.formIdProp.setValue(BufferUtil.cloneBuffer(getFormId()));
    copy.versionProp.setValue(getVersion());
    copy.formKeyProp.setValue(getFormKey());
    copy.resourceNameProp.setValue(BufferUtil.cloneBuffer(getResourceName()));
    copy.resourceProp.setValue(BufferUtil.cloneBuffer(getResource()));
    copy.checksumProp.setValue(BufferUtil.cloneBuffer(getChecksum()));
    copy.tenantIdProp.setValue(getTenantId());
    copy.deploymentKeyProp.setValue(getDeploymentKey());
    return copy;
  }

  public DirectBuffer getFormId() {
    return formIdProp.getValue();
  }

  public int getVersion() {
    return versionProp.getValue();
  }

  public long getFormKey() {
    return formKeyProp.getValue();
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public DirectBuffer getResource() {
    return resourceProp.getValue();
  }

  public DirectBuffer getChecksum() {
    return checksumProp.getValue();
  }

  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public long getDeploymentKey() {
    return deploymentKeyProp.getValue();
  }

  public void wrap(final FormRecord record) {
    formIdProp.setValue(record.getFormId());
    versionProp.setValue(record.getVersion());
    formKeyProp.setValue(record.getFormKey());
    resourceNameProp.setValue(record.getResourceNameBuffer());
    resourceProp.setValue(BufferUtil.wrapArray(record.getResource()));
    checksumProp.setValue(record.getChecksumBuffer());
    tenantIdProp.setValue(record.getTenantId());
    deploymentKeyProp.setValue(record.getDeploymentKey());
  }
}

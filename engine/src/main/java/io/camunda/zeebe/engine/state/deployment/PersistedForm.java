/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class PersistedForm extends UnpackedObject implements DbValue {
  private final StringProperty formIdProp = new StringProperty("formId");
  private final IntegerProperty versionProp = new IntegerProperty("version");
  private final LongProperty formKeyProp = new LongProperty("formKey");
  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());
  private final BinaryProperty resourceProp = new BinaryProperty("resource", new UnsafeBuffer());

  public PersistedForm() {
    declareProperty(formIdProp)
        .declareProperty(versionProp)
        .declareProperty(formKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceProp);
  }

  public DirectBuffer getFormIdProp() {
    return formIdProp.getValue();
  }

  public int getVersionProp() {
    return versionProp.getValue();
  }

  public long getFormKeyProp() {
    return formKeyProp.getValue();
  }

  public DirectBuffer getResourceNameProp() {
    return resourceNameProp.getValue();
  }

  public DirectBuffer getChecksumProp() {
    return checksumProp.getValue();
  }

  public DirectBuffer getResourceProp() {
    return resourceProp.getValue();
  }

  public void wrap(final FormRecord record) {
    formIdProp.setValue(record.getFormId());
    versionProp.setValue(record.getVersion());
    checksumProp.setValue(record.getChecksumBuffer());
    formKeyProp.setValue(record.getFormKey());
    resourceNameProp.setValue(record.getResourceNameBuffer());
    resourceProp.setValue(BufferUtil.wrapArray(record.getResource()));
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

@JsonIgnoreProperties({
  /* Inherited from ObjectValue. They have no purpose in exported JSON records. */
  "encodedLength",
  "empty"
})
public final class JobSecretReference extends ObjectValue implements JobSecretReferenceValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue STORE_ID_KEY = new StringValue("storeId");
  private static final StringValue SECRET_REFERENCE_KEY = new StringValue("secretReference");
  private static final StringValue PATH_KEY = new StringValue("path");

  private final StringProperty storeIdProp = new StringProperty(STORE_ID_KEY, "");
  private final StringProperty secretReferenceProp = new StringProperty(SECRET_REFERENCE_KEY, "");
  // RFC 6901 JSON pointer where the resolved secret is injected into the job variables on
  // activation
  private final StringProperty pathProp = new StringProperty(PATH_KEY, "");

  public JobSecretReference() {
    super(3);
    declareProperty(storeIdProp).declareProperty(secretReferenceProp).declareProperty(pathProp);
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeIdProp.getValue());
  }

  public JobSecretReference setStoreId(final String storeId) {
    storeIdProp.setValue(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReferenceProp.getValue());
  }

  public JobSecretReference setSecretReference(final String secretReference) {
    secretReferenceProp.setValue(secretReference);
    return this;
  }

  @Override
  public String getPath() {
    return BufferUtil.bufferAsString(pathProp.getValue());
  }

  public JobSecretReference setPath(final String path) {
    pathProp.setValue(path);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getStoreIdBuffer() {
    return storeIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getSecretReferenceBuffer() {
    return secretReferenceProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getPathBuffer() {
    return pathProp.getValue();
  }

  public void copy(final JobSecretReferenceValue secretReference) {
    setStoreId(secretReference.getStoreId());
    setSecretReference(secretReference.getSecretReference());
    setPath(secretReference.getPath());
  }

  /** Copies the values buffer-to-buffer, avoiding the String round-trip of {@link #copy}. */
  public JobSecretReference copyFrom(final JobSecretReference other) {
    storeIdProp.setValue(other.getStoreIdBuffer());
    secretReferenceProp.setValue(other.getSecretReferenceBuffer());
    pathProp.setValue(other.getPathBuffer());
    return this;
  }
}

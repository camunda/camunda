/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* Inherited from ObjectValue. They have no purpose in exported JSON records. */
  "encodedLength",
  "empty"
})
public final class ClusterVariableSecretReference extends ObjectValue
    implements ClusterVariableSecretReferenceValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue STORE_ID_KEY = new StringValue("storeId");
  private static final StringValue SECRET_REFERENCE_KEY = new StringValue("secretReference");
  private static final StringValue PATH_KEY = new StringValue("path");

  // mirrors io.camunda.secretstore.SecretStoreRegistry#DEFAULT_STORE_ID: the record layer stays
  // free of the secret store API, so the value is repeated here and pinned against the constant
  // by DefaultStoreIdTest. An unset store ID names the default store, not no store at all.
  private final StringProperty storeIdProp = new StringProperty(STORE_ID_KEY, "default");
  private final StringProperty secretReferenceProp = new StringProperty(SECRET_REFERENCE_KEY, "");
  // RFC 6901 JSON pointer of the value leaf the reference was found in
  private final StringProperty pathProp = new StringProperty(PATH_KEY, "");

  public ClusterVariableSecretReference() {
    super(3);
    declareProperty(storeIdProp).declareProperty(secretReferenceProp).declareProperty(pathProp);
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeIdProp.getValue());
  }

  public ClusterVariableSecretReference setStoreId(final String storeId) {
    storeIdProp.setValue(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReferenceProp.getValue());
  }

  public ClusterVariableSecretReference setSecretReference(final String secretReference) {
    secretReferenceProp.setValue(secretReference);
    return this;
  }

  @Override
  public String getPath() {
    return BufferUtil.bufferAsString(pathProp.getValue());
  }

  public ClusterVariableSecretReference setPath(final String path) {
    pathProp.setValue(path);
    return this;
  }

  public void copy(final ClusterVariableSecretReferenceValue secretReference) {
    setStoreId(secretReference.getStoreId());
    setSecretReference(secretReference.getSecretReference());
    setPath(secretReference.getPath());
  }
}

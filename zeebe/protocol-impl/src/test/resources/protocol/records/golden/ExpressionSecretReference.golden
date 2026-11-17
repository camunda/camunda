/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue.ExpressionSecretReferenceValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

@JsonIgnoreProperties({
  /* Inherited from ObjectValue. They have no purpose in exported JSON records. */
  "encodedLength",
  "empty"
})
public final class ExpressionSecretReference extends ObjectValue
    implements ExpressionSecretReferenceValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue STORE_ID_KEY = new StringValue("storeId");
  private static final StringValue SECRET_REFERENCE_KEY = new StringValue("secretReference");

  // mirrors io.camunda.secretstore.SecretStoreRegistry#DEFAULT_STORE_ID: the record layer stays
  // free of the secret store API, so the value is repeated here. An unset store ID names the
  // default store, not no store at all.
  private final StringProperty storeIdProp = new StringProperty(STORE_ID_KEY, "default");
  private final StringProperty secretReferenceProp = new StringProperty(SECRET_REFERENCE_KEY, "");

  public ExpressionSecretReference() {
    super(2);
    declareProperty(storeIdProp).declareProperty(secretReferenceProp);
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeIdProp.getValue());
  }

  public ExpressionSecretReference setStoreId(final String storeId) {
    storeIdProp.setValue(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReferenceProp.getValue());
  }

  public ExpressionSecretReference setSecretReference(final String secretReference) {
    secretReferenceProp.setValue(secretReference);
    return this;
  }
}

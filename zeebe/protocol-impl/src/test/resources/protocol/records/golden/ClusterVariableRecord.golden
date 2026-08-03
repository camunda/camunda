/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ClusterVariableRecord extends UnifiedRecordValue
    implements ClusterVariableRecordValue {

  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue VALUE_KEY = new StringValue("value");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue SCOPE_KEY = new StringValue("scope");
  private static final StringValue METADATA_KEY = new StringValue("metadata");
  private static final StringValue KIND_KEY = new StringValue("kind");
  private static final StringValue SECRET_REFERENCES_KEY = new StringValue("secretReferences");

  private final StringProperty nameProp = new StringProperty(NAME_KEY);
  private final BinaryProperty valueProp =
      new BinaryProperty(VALUE_KEY, new UnsafeBuffer(new byte[] {0}));
  private final EnumProperty<ClusterVariableScope> scopeProp =
      new EnumProperty<>(SCOPE_KEY, ClusterVariableScope.class, ClusterVariableScope.UNSPECIFIED);
  private final StringProperty tenantIdProp = new StringProperty(TENANT_ID_KEY, "");
  private final DocumentProperty metadataProp = new DocumentProperty(METADATA_KEY);
  private final EnumProperty<ClusterVariableKind> kindProp =
      new EnumProperty<>(KIND_KEY, ClusterVariableKind.class, ClusterVariableKind.JSON);
  private final ArrayProperty<ClusterVariableSecretReference> secretReferencesProp =
      new ArrayProperty<>(SECRET_REFERENCES_KEY, ClusterVariableSecretReference::new);

  public ClusterVariableRecord() {
    super(7);
    declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(scopeProp)
        .declareProperty(tenantIdProp)
        .declareProperty(metadataProp)
        .declareProperty(kindProp)
        .declareProperty(secretReferencesProp);
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  @Override
  public String getValue() {
    return MsgPackConverter.convertToJson(valueProp.getValue());
  }

  public ClusterVariableRecord setValue(final DirectBuffer value) {
    valueProp.setValue(value);
    return this;
  }

  @Override
  public ClusterVariableScope getScope() {
    return scopeProp.getValue();
  }

  public ClusterVariableRecord setScope(final ClusterVariableScope scope) {
    scopeProp.setValue(scope);
    return this;
  }

  public ClusterVariableRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public ClusterVariableRecord setTenantScope() {
    return setScope(ClusterVariableScope.TENANT);
  }

  public ClusterVariableRecord setGlobalScope() {
    return setScope(ClusterVariableScope.GLOBAL);
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getValueBuffer() {
    return valueProp.getValue();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ClusterVariableRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public Map<String, Object> getMetadata() {
    return MsgPackConverter.convertToMap(metadataProp.getValue());
  }

  public ClusterVariableRecord setMetadata(final DirectBuffer metadata) {
    metadataProp.setValue(metadata);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMetadataBuffer() {
    return metadataProp.getValue();
  }

  @Override
  public ClusterVariableKind getKind() {
    return kindProp.getValue();
  }

  public ClusterVariableRecord setKind(final ClusterVariableKind kind) {
    kindProp.setValue(kind);
    return this;
  }

  @JsonIgnore
  public boolean isTenantScoped() {
    return ClusterVariableScope.TENANT.equals(scopeProp.getValue());
  }

  @JsonIgnore
  public boolean isGloballyScoped() {
    return ClusterVariableScope.GLOBAL.equals(scopeProp.getValue());
  }

  @Override
  public List<ClusterVariableSecretReferenceValue> getSecretReferences() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return secretReferencesProp.stream()
        .map(
            element -> {
              final var copy = new ClusterVariableSecretReference();
              copy.copy(element);
              return (ClusterVariableSecretReferenceValue) copy;
            })
        .toList();
  }

  /**
   * Direct access to the secret reference elements, without the detached copies of {@link
   * #getSecretReferences()}. The elements stay owned by this record; do not hold on to them.
   */
  @JsonIgnore
  public ValueArray<ClusterVariableSecretReference> secretReferences() {
    return secretReferencesProp;
  }

  @JsonIgnore
  public boolean hasSecretReferences() {
    return !secretReferencesProp.isEmpty();
  }

  public ClusterVariableRecord addSecretReference(
      final String storeId, final String secretReference, final String path) {
    secretReferencesProp
        .add()
        .setStoreId(storeId)
        .setSecretReference(secretReference)
        .setPath(path);
    return this;
  }
}

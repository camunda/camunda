/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue.ClusterVariableSecretReferenceValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class ClusterVariableRecord extends MsgpackBufferValue<ClusterVariableRecord>
    implements MsgpackRecordValue, ClusterVariableRecordValue {

  @Nullable DirectBuffer name;

  @MsgpackProperty(binary = true)
  DirectBuffer value = new UnsafeBuffer(new byte[] {0});

  ClusterVariableScope scope = ClusterVariableScope.UNSPECIFIED;
  DirectBuffer tenantId = Decoding.EMPTY;

  @MsgpackProperty(document = true)
  DirectBuffer metadata = DocumentValue.EMPTY_DOCUMENT;

  ClusterVariableKind kind = ClusterVariableKind.JSON;
  final List<ClusterVariableSecretReference> secretReferences = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(Decoding.required(name, "name"));
  }

  @Override
  public String getValue() {
    return MsgPackConverter.convertToJson(value);
  }

  public ClusterVariableRecord setValue(final DirectBuffer value) {
    this.value = value;
    return this;
  }

  @Override
  public ClusterVariableScope getScope() {
    return scope;
  }

  public ClusterVariableRecord setScope(final ClusterVariableScope scope) {
    this.scope = scope;
    return this;
  }

  public ClusterVariableRecord setName(final String name) {
    this.name = BufferUtil.wrapString(name);
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
    return Decoding.required(name, "name");
  }

  @JsonIgnore
  public DirectBuffer getValueBuffer() {
    return value;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ClusterVariableRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public Map<String, Object> getMetadata() {
    return MsgPackConverter.convertToMap(metadata);
  }

  public ClusterVariableRecord setMetadata(final DirectBuffer metadata) {
    this.metadata = Decoding.document(metadata);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getMetadataBuffer() {
    return metadata;
  }

  @Override
  public ClusterVariableKind getKind() {
    return kind;
  }

  public ClusterVariableRecord setKind(final ClusterVariableKind kind) {
    this.kind = kind;
    return this;
  }

  @JsonIgnore
  public boolean isTenantScoped() {
    return ClusterVariableScope.TENANT.equals(scope);
  }

  @JsonIgnore
  public boolean isGloballyScoped() {
    return ClusterVariableScope.GLOBAL.equals(scope);
  }

  @Override
  public List<ClusterVariableSecretReferenceValue> getSecretReferences() {
    // detach copies so the returned list stays valid if this record is reused or reset later
    return secretReferences.stream()
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
    return new SecretReferences(secretReferences);
  }

  @JsonIgnore
  public boolean hasSecretReferences() {
    return !secretReferences.isEmpty();
  }

  public ClusterVariableRecord addSecretReference(
      final String storeId, final String secretReference, final String path) {
    secretReferences.add(
        new ClusterVariableSecretReference()
            .setStoreId(storeId)
            .setSecretReference(secretReference)
            .setPath(path));
    return this;
  }

  @Override
  protected MsgpackCodec<ClusterVariableRecord> codec() {
    return ClusterVariableRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ClusterVariableRecord> ops() {
    return ClusterVariableRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of {@code secretReferences}: it adds and removes the elements of the list,
   * so that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class SecretReferences
      implements ValueArray<ClusterVariableSecretReference> {

    final List<ClusterVariableSecretReference> references;

    private SecretReferences(final List<ClusterVariableSecretReference> references) {
      this.references = references;
    }

    @Override
    public ClusterVariableSecretReference add() {
      final var reference = new ClusterVariableSecretReference();
      references.add(reference);
      return reference;
    }

    @Override
    public ClusterVariableSecretReference add(final int index) {
      final var reference = new ClusterVariableSecretReference();
      references.add(index, reference);
      return reference;
    }

    @Override
    public ClusterVariableSecretReference get(final int index) {
      return references.get(index);
    }

    @Override
    public ClusterVariableSecretReference remove(final int index) {
      return references.remove(index);
    }

    @Override
    public int size() {
      return references.size();
    }

    @Override
    public Stream<ClusterVariableSecretReference> stream() {
      return references.stream();
    }

    @Override
    public Iterator<ClusterVariableSecretReference> iterator() {
      return references.iterator();
    }
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredInt;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class ProcessRecord extends MsgpackBufferValue<ProcessRecord>
    implements MsgpackRecordValue, Process {

  @Nullable DirectBuffer bpmnProcessId;
  final RequiredInt version = new RequiredInt();
  final RequiredLong processDefinitionKey = new RequiredLong();
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  DirectBuffer checksum = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  DirectBuffer resource = Decoding.EMPTY;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = -1L;
  DirectBuffer versionTag = Decoding.EMPTY;
  final List<TransformerVersion> transformerVersions = new ArrayList<>();
  boolean deleteHistory = false;
  final IntArrayList drainPartitions = new IntArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public ProcessRecord wrap(final ProcessMetadata metadata, final byte[] resource) {
    bpmnProcessId = metadata.getBpmnProcessIdBuffer();
    version.set(metadata.getVersion());
    checksum = metadata.getChecksumBuffer();
    processDefinitionKey.set(metadata.getKey());
    resourceName = metadata.getResourceNameBuffer();
    this.resource = BufferUtil.wrapArray(resource);
    tenantId = BufferUtil.wrapString(metadata.getTenantId());
    deploymentKey = metadata.getDeploymentKey();
    versionTag = BufferUtil.wrapString(metadata.getVersionTag());
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(Decoding.required(bpmnProcessId, "bpmnProcessId"));
  }

  @Override
  public int getVersion() {
    return version.get();
  }

  public ProcessRecord setVersion(final int version) {
    this.version.set(version);
    return this;
  }

  @Override
  public String getVersionTag() {
    return BufferUtil.bufferAsString(versionTag);
  }

  @Override
  public long getProcessDefinitionKey() {
    return getKey();
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(Decoding.required(resourceName, "resourceName"));
  }

  @Override
  public byte[] getChecksum() {
    return BufferUtil.bufferAsArray(checksum);
  }

  @Override
  public boolean isDuplicate() {
    return false;
  }

  @Override
  public long getDeploymentKey() {
    return deploymentKey;
  }

  public ProcessRecord setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  public ProcessRecord setChecksum(final DirectBuffer checksumBuffer) {
    checksum = new UnsafeBuffer(checksumBuffer, 0, checksumBuffer.capacity());
    return this;
  }

  public ProcessRecord setResourceName(final String resourceName) {
    this.resourceName = BufferUtil.wrapString(resourceName);
    return this;
  }

  public ProcessRecord setResourceName(final DirectBuffer resourceName) {
    this.resourceName = new UnsafeBuffer(resourceName, 0, resourceName.capacity());
    return this;
  }

  public ProcessRecord setVersionTag(final String versionTag) {
    this.versionTag = BufferUtil.wrapString(versionTag);
    return this;
  }

  public ProcessRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public ProcessRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = new UnsafeBuffer(bpmnProcessId, 0, bpmnProcessId.capacity());
    return this;
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksum;
  }

  @Override
  public byte[] getResource() {
    return BufferUtil.bufferAsArray(resource);
  }

  public ProcessRecord setResource(final DirectBuffer resource) {
    return setResource(resource, 0, resource.capacity());
  }

  @JsonIgnore
  public long getKey() {
    return processDefinitionKey.get();
  }

  public ProcessRecord setKey(final long key) {
    processDefinitionKey.set(key);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return Decoding.required(bpmnProcessId, "bpmnProcessId");
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return Decoding.required(resourceName, "resourceName");
  }

  public ProcessRecord setBpmnProcessId(
      final DirectBuffer bpmnProcessId, final int offset, final int length) {
    this.bpmnProcessId = new UnsafeBuffer(bpmnProcessId, offset, length);
    return this;
  }

  public ProcessRecord setResource(
      final DirectBuffer resource, final int offset, final int length) {
    this.resource = new UnsafeBuffer(resource, offset, length);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getResourceBuffer() {
    return resource;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ProcessRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  /**
   * Returns the stored slot→version pairs sorted by slot ID. Absent entries default to version 1.
   * Excluded from JSON export — engine-internal state not exposed to external consumers.
   */
  @JsonIgnore
  public Map<Integer, Integer> getTransformerVersions() {
    final Map<Integer, Integer> result = new TreeMap<>();
    for (final TransformerVersion entry : transformerVersions) {
      result.put(entry.getSlotId(), entry.getVersion());
    }
    return result;
  }

  /**
   * Sets the per-slot transformer versions frozen at deploy time. Entries are sorted by slot ID
   * before writing so the msgpack array is always in a deterministic order regardless of the input
   * map's iteration order. Version-1 entries are stored but redundant — {@link
   * io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer#currentVersionsById()}
   * omits them automatically, so callers using that method need not filter.
   */
  public ProcessRecord setTransformerVersions(final Map<Integer, Integer> versions) {
    transformerVersions.clear();
    new TreeMap<>(versions)
        .forEach(
            (slotId, version) ->
                transformerVersions.add(
                    new TransformerVersion().setSlotId(slotId).setVersion(version)));
    return this;
  }

  /**
   * Whether the instances' history must be deleted once the definition is gone cluster-wide.
   * Carried on {@code DRAINING} to be persisted with the draining definition, then back on the
   * {@code DELETE_COMPLETE} drain report so the deployment partition knows whether to delete it.
   * Excluded from JSON export — engine-internal coordination.
   */
  @JsonIgnore
  public boolean isDeleteHistory() {
    return deleteHistory;
  }

  public ProcessRecord setDeleteHistory(final boolean deleteHistory) {
    this.deleteHistory = deleteHistory;
    return this;
  }

  /** Partitions frozen at DRAINING time to seed drain-aggregation. Only set on DRAINING events. */
  @JsonIgnore
  public List<Integer> getDrainPartitions() {
    final List<Integer> result = new ArrayList<>();
    for (int i = 0; i < drainPartitions.size(); i++) {
      result.add(drainPartitions.getInt(i));
    }
    return result;
  }

  public ProcessRecord setDrainPartitions(final Collection<Integer> partitionIds) {
    drainPartitions.clear();
    // Sort to keep the serialized record deterministic across runs.
    partitionIds.stream().sorted().forEach(drainPartitions::addInt);
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessRecord> codec() {
    return ProcessRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessRecord> ops() {
    return ProcessRecordOps.INSTANCE;
  }

  /** A single (slotId, version) pair persisted inside the transformer-versions array. */
  public static final class TransformerVersion extends UnpackedObject {
    private final IntegerProperty slotIdProp = new IntegerProperty("slotId");
    private final IntegerProperty versionProp = new IntegerProperty("version");

    public TransformerVersion() {
      super(2);
      declareProperty(slotIdProp).declareProperty(versionProp);
    }

    public int getSlotId() {
      return slotIdProp.getValue();
    }

    public TransformerVersion setSlotId(final int slotId) {
      slotIdProp.setValue(slotId);
      return this;
    }

    public int getVersion() {
      return versionProp.getValue();
    }

    public TransformerVersion setVersion(final int version) {
      versionProp.setValue(version);
      return this;
    }
  }
}

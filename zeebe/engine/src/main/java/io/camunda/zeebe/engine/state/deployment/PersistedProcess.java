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
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformerSlot;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord.TransformerVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.ProjectionOf;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class PersistedProcess extends MsgpackBufferValue<PersistedProcess>
    implements DbValue, ProjectionOf<Tuple<ProcessRecord, Long>, PersistedProcess> {
  private static final long NO_DEPLOYMENT_KEY = -1L;

  int version = -1;
  long key = -1L;
  @Nullable DirectBuffer bpmnProcessId;
  @Nullable DirectBuffer resourceName;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer resource;

  PersistedProcessState state = PersistedProcessState.ACTIVE;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long deploymentKey = NO_DEPLOYMENT_KEY;
  DirectBuffer versionTag = Decoding.EMPTY;
  final List<TransformerVersion> transformerVersions = new ArrayList<>();
  boolean deleteHistory = false;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public PersistedProcess wrap(final Tuple<ProcessRecord, Long> source) {
    wrap(source.getLeft(), source.getRight());
    return this;
  }

  public void wrap(final ProcessRecord processRecord, final long processDefinitionKey) {
    reset();
    bpmnProcessId = processRecord.getBpmnProcessIdBuffer();
    resourceName = processRecord.getResourceNameBuffer();
    resource = processRecord.getResourceBuffer();

    version = processRecord.getVersion();
    key = processDefinitionKey;
    tenantId = BufferUtil.wrapString(processRecord.getTenantId());
    deploymentKey = processRecord.getDeploymentKey();
    versionTag = BufferUtil.wrapString(processRecord.getVersionTag());

    processRecord
        .getTransformerVersions()
        .forEach(
            (slotId, slotVersion) -> {
              final var entry = new TransformerVersion();
              entry.setSlotId(slotId);
              entry.setVersion(slotVersion);
              transformerVersions.add(entry);
            });
  }

  /**
   * Returns the per-slot transformer versions frozen at deploy time. Only slots with versions
   * greater than the default (1) are stored; absent slots resolve to version 1.
   */
  public Map<TransformerSlot, Integer> getTransformerVersions() {
    final Map<TransformerSlot, Integer> result = new EnumMap<>(TransformerSlot.class);
    for (final TransformerVersion entry : transformerVersions) {
      final TransformerSlot slot = TransformerSlot.fromId(entry.getSlotId());
      result.put(slot, entry.getVersion());
    }
    return result;
  }

  public int getVersion() {
    return version;
  }

  public String getVersionTag() {
    return bufferAsString(versionTag);
  }

  public long getKey() {
    return key;
  }

  public DirectBuffer getBpmnProcessId() {
    return Decoding.required(bpmnProcessId, "bpmnProcessId");
  }

  public DirectBuffer getResourceName() {
    return Decoding.required(resourceName, "resourceName");
  }

  public DirectBuffer getResource() {
    return Decoding.required(resource, "resource");
  }

  public PersistedProcessState getState() {
    return state;
  }

  public PersistedProcess setState(final PersistedProcessState state) {
    this.state = state;
    return this;
  }

  public boolean isDeleteHistory() {
    return deleteHistory;
  }

  public PersistedProcess setDeleteHistory(final boolean deleteHistory) {
    this.deleteHistory = deleteHistory;
    return this;
  }

  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public PersistedProcess setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  public boolean hasDeploymentKey() {
    return deploymentKey != NO_DEPLOYMENT_KEY;
  }

  public long getDeploymentKey() {
    return deploymentKey;
  }

  public PersistedProcess setDeploymentKey(final long deploymentKey) {
    this.deploymentKey = deploymentKey;
    return this;
  }

  @Override
  protected MsgpackCodec<PersistedProcess> codec() {
    return PersistedProcessCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PersistedProcess> ops() {
    return PersistedProcessOps.INSTANCE;
  }

  public enum PersistedProcessState {
    ACTIVE(0),
    PENDING_DELETION(1),
    DRAINING(2);

    byte value;

    PersistedProcessState(final int value) {
      this.value = (byte) value;
    }
  }
}

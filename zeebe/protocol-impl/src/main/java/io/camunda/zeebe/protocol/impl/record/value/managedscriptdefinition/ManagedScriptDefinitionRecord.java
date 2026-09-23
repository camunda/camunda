/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.managedscriptdefinition;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionRecordValue;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ManagedScriptDefinitionRecord extends UnifiedRecordValue
    implements ManagedScriptDefinitionRecordValue {

  private final LongProperty managedScriptDefinitionKeyProp =
      new LongProperty("managedScriptDefinitionKey", -1L);
  private final EnumProperty<ManagedScriptDefinitionStatus> statusProp =
      new EnumProperty<>(
          "status", ManagedScriptDefinitionStatus.class, ManagedScriptDefinitionStatus.UNSPECIFIED);
  private final LongProperty revisionProp = new LongProperty("revision", 0L);
  private final LongProperty resourceKeyProp = new LongProperty("resourceKey", -1L);
  private final StringProperty resourceNameProp = new StringProperty("resourceName", "");
  private final BinaryProperty artifactDigestProp =
      new BinaryProperty("artifactDigest", new UnsafeBuffer());
  private final StringProperty elementIdProp = new StringProperty("elementId", "");
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final IntegerProperty processDefinitionVersionProp =
      new IntegerProperty("processDefinitionVersion", -1);
  private final StringProperty processDefinitionVersionTagProp =
      new StringProperty("processDefinitionVersionTag", "");
  private final StringProperty languageProp = new StringProperty("language", "");
  private final StringProperty runtimeProp = new StringProperty("runtime", "");
  private final StringProperty providerProp = new StringProperty("provider", "");
  private final StringProperty leaseOwnerProp = new StringProperty("leaseOwner", "");
  private final StringProperty leaseTokenProp = new StringProperty("leaseToken", "");
  private final LongProperty leaseExpiresAtProp = new LongProperty("leaseExpiresAt", -1L);
  private final LongProperty leaseDurationProp = new LongProperty("leaseDuration", -1L);
  private final StringProperty providerOperationIdProp =
      new StringProperty("providerOperationId", "");
  private final StringProperty providerDeploymentIdProp =
      new StringProperty("providerDeploymentId", "");
  private final StringProperty failureCodeProp = new StringProperty("failureCode", "");
  private final StringProperty failureMessageProp = new StringProperty("failureMessage", "");
  private final BooleanProperty retryableProp = new BooleanProperty("retryable", false);
  private final StringProperty operationIdProp = new StringProperty("operationId", "");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ManagedScriptDefinitionRecord() {
    super(25);
    declareProperty(managedScriptDefinitionKeyProp)
        .declareProperty(statusProp)
        .declareProperty(revisionProp)
        .declareProperty(resourceKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(artifactDigestProp)
        .declareProperty(elementIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(processDefinitionKeyProp)
        .declareProperty(processDefinitionVersionProp)
        .declareProperty(processDefinitionVersionTagProp)
        .declareProperty(languageProp)
        .declareProperty(runtimeProp)
        .declareProperty(providerProp)
        .declareProperty(leaseOwnerProp)
        .declareProperty(leaseTokenProp)
        .declareProperty(leaseExpiresAtProp)
        .declareProperty(leaseDurationProp)
        .declareProperty(providerOperationIdProp)
        .declareProperty(providerDeploymentIdProp)
        .declareProperty(failureCodeProp)
        .declareProperty(failureMessageProp)
        .declareProperty(retryableProp)
        .declareProperty(operationIdProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public long getManagedScriptDefinitionKey() {
    return managedScriptDefinitionKeyProp.getValue();
  }

  public ManagedScriptDefinitionRecord setManagedScriptDefinitionKey(
      final long managedScriptDefinitionKey) {
    managedScriptDefinitionKeyProp.setValue(managedScriptDefinitionKey);
    return this;
  }

  @Override
  public ManagedScriptDefinitionStatus getStatus() {
    return statusProp.getValue();
  }

  public ManagedScriptDefinitionRecord setStatus(final ManagedScriptDefinitionStatus status) {
    statusProp.setValue(status);
    return this;
  }

  @Override
  public long getRevision() {
    return revisionProp.getValue();
  }

  public ManagedScriptDefinitionRecord setRevision(final long revision) {
    revisionProp.setValue(revision);
    return this;
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public ManagedScriptDefinitionRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @Override
  public String getResourceName() {
    return BufferUtil.bufferAsString(resourceNameProp.getValue());
  }

  public ManagedScriptDefinitionRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  @Override
  public byte[] getArtifactDigest() {
    return BufferUtil.bufferAsArray(artifactDigestProp.getValue());
  }

  public ManagedScriptDefinitionRecord setArtifactDigest(final DirectBuffer artifactDigest) {
    artifactDigestProp.setValue(artifactDigest);
    return this;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementIdProp.getValue());
  }

  public ManagedScriptDefinitionRecord setElementId(final String elementId) {
    elementIdProp.setValue(elementId);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public ManagedScriptDefinitionRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ManagedScriptDefinitionRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersionProp.getValue();
  }

  public ManagedScriptDefinitionRecord setProcessDefinitionVersion(
      final int processDefinitionVersion) {
    processDefinitionVersionProp.setValue(processDefinitionVersion);
    return this;
  }

  @Override
  public String getProcessDefinitionVersionTag() {
    return BufferUtil.bufferAsString(processDefinitionVersionTagProp.getValue());
  }

  public ManagedScriptDefinitionRecord setProcessDefinitionVersionTag(
      final String processDefinitionVersionTag) {
    processDefinitionVersionTagProp.setValue(processDefinitionVersionTag);
    return this;
  }

  @Override
  public String getLanguage() {
    return BufferUtil.bufferAsString(languageProp.getValue());
  }

  public ManagedScriptDefinitionRecord setLanguage(final String language) {
    languageProp.setValue(language);
    return this;
  }

  @Override
  public String getRuntime() {
    return BufferUtil.bufferAsString(runtimeProp.getValue());
  }

  public ManagedScriptDefinitionRecord setRuntime(final String runtime) {
    runtimeProp.setValue(runtime);
    return this;
  }

  @Override
  public String getProvider() {
    return BufferUtil.bufferAsString(providerProp.getValue());
  }

  public ManagedScriptDefinitionRecord setProvider(final String provider) {
    providerProp.setValue(provider);
    return this;
  }

  @Override
  public String getLeaseOwner() {
    return BufferUtil.bufferAsString(leaseOwnerProp.getValue());
  }

  public ManagedScriptDefinitionRecord setLeaseOwner(final String leaseOwner) {
    leaseOwnerProp.setValue(leaseOwner);
    return this;
  }

  @Override
  public String getLeaseToken() {
    return BufferUtil.bufferAsString(leaseTokenProp.getValue());
  }

  public ManagedScriptDefinitionRecord setLeaseToken(final String leaseToken) {
    leaseTokenProp.setValue(leaseToken);
    return this;
  }

  @Override
  public long getLeaseExpiresAt() {
    return leaseExpiresAtProp.getValue();
  }

  public ManagedScriptDefinitionRecord setLeaseExpiresAt(final long leaseExpiresAt) {
    leaseExpiresAtProp.setValue(leaseExpiresAt);
    return this;
  }

  @Override
  public long getLeaseDuration() {
    return leaseDurationProp.getValue();
  }

  public ManagedScriptDefinitionRecord setLeaseDuration(final long leaseDuration) {
    leaseDurationProp.setValue(leaseDuration);
    return this;
  }

  @Override
  public String getProviderOperationId() {
    return BufferUtil.bufferAsString(providerOperationIdProp.getValue());
  }

  public ManagedScriptDefinitionRecord setProviderOperationId(final String providerOperationId) {
    providerOperationIdProp.setValue(providerOperationId);
    return this;
  }

  @Override
  public String getProviderDeploymentId() {
    return BufferUtil.bufferAsString(providerDeploymentIdProp.getValue());
  }

  public ManagedScriptDefinitionRecord setProviderDeploymentId(final String providerDeploymentId) {
    providerDeploymentIdProp.setValue(providerDeploymentId);
    return this;
  }

  @Override
  public String getFailureCode() {
    return BufferUtil.bufferAsString(failureCodeProp.getValue());
  }

  public ManagedScriptDefinitionRecord setFailureCode(final String failureCode) {
    failureCodeProp.setValue(failureCode);
    return this;
  }

  @Override
  public String getFailureMessage() {
    return BufferUtil.bufferAsString(failureMessageProp.getValue());
  }

  public ManagedScriptDefinitionRecord setFailureMessage(final String failureMessage) {
    failureMessageProp.setValue(failureMessage);
    return this;
  }

  @Override
  public boolean isRetryable() {
    return retryableProp.getValue();
  }

  public ManagedScriptDefinitionRecord setRetryable(final boolean retryable) {
    retryableProp.setValue(retryable);
    return this;
  }

  @Override
  public String getOperationId() {
    return BufferUtil.bufferAsString(operationIdProp.getValue());
  }

  public ManagedScriptDefinitionRecord setOperationId(final String operationId) {
    operationIdProp.setValue(operationId);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ManagedScriptDefinitionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}

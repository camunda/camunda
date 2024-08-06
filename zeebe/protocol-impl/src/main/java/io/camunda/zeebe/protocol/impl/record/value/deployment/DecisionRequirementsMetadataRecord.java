/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DecisionRequirementsMetadataRecord extends UnifiedRecordValue
    implements DecisionRequirementsMetadataValue {

  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId", "");
  private final StringProperty decisionRequirementsNameProp =
      new StringProperty("decisionRequirementsName", "");
  private final IntegerProperty decisionRequirementsVersionProp =
      new IntegerProperty("decisionRequirementsVersion", -1);
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey", -1);
  private final StringProperty namespaceProp = new StringProperty("namespace", "");

  private final StringProperty resourceNameProp = new StringProperty("resourceName", "");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum", new UnsafeBuffer());

  private final BooleanProperty isDuplicateProp = new BooleanProperty("isDuplicate", false);
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public DecisionRequirementsMetadataRecord() {
    super(9);
    declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsNameProp)
        .declareProperty(decisionRequirementsVersionProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(namespaceProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(isDuplicateProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getDecisionRequirementsId() {
    return bufferAsString(decisionRequirementsIdProp.getValue());
  }

  @Override
  public String getDecisionRequirementsName() {
    return bufferAsString(decisionRequirementsNameProp.getValue());
  }

  @Override
  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersionProp.getValue();
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKeyProp.getValue();
  }

  @Override
  public String getNamespace() {
    return bufferAsString(namespaceProp.getValue());
  }

  @Override
  public String getResourceName() {
    return bufferAsString(resourceNameProp.getValue());
  }

  @Override
  public byte[] getChecksum() {
    return bufferAsArray(checksumProp.getValue());
  }

  @Override
  public boolean isDuplicate() {
    return isDuplicateProp.getValue();
  }

  public DecisionRequirementsMetadataRecord setDuplicate(final boolean duplicate) {
    isDuplicateProp.setValue(duplicate);
    return this;
  }

  public DecisionRequirementsMetadataRecord setChecksum(final DirectBuffer checksum) {
    checksumProp.setValue(checksum);
    return this;
  }

  public DecisionRequirementsMetadataRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public DecisionRequirementsMetadataRecord setNamespace(final String namespace) {
    namespaceProp.setValue(namespace);
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsKey(
      final long decisionRequirementsKey) {
    decisionRequirementsKeyProp.setValue(decisionRequirementsKey);
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsVersion(
      final int decisionRequirementsVersion) {
    decisionRequirementsVersionProp.setValue(decisionRequirementsVersion);
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsName(
      final String decisionRequirementsName) {
    decisionRequirementsNameProp.setValue(decisionRequirementsName);
    return this;
  }

  public DecisionRequirementsMetadataRecord setDecisionRequirementsId(
      final String decisionRequirementsId) {
    decisionRequirementsIdProp.setValue(decisionRequirementsId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsIdBuffer() {
    return decisionRequirementsIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getDecisionRequirementsNameBuffer() {
    return decisionRequirementsNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getNamespaceBuffer() {
    return namespaceProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResourceNameBuffer() {
    return resourceNameProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public DecisionRequirementsMetadataRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}

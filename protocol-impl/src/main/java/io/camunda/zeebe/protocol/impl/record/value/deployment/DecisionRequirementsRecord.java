/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import org.agrona.DirectBuffer;

public final class DecisionRequirementsRecord extends UnifiedRecordValue
    implements DecisionRequirementsRecordValue {

  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId");
  private final StringProperty decisionRequirementsNameProp =
      new StringProperty("decisionRequirementsName");
  private final IntegerProperty decisionRequirementsVersionProp =
      new IntegerProperty("decisionRequirementsVersion");
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey");
  private final StringProperty namespaceProp = new StringProperty("namespace", "");

  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final BinaryProperty resourceProp = new BinaryProperty("resource");

  public DecisionRequirementsRecord() {
    declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsNameProp)
        .declareProperty(decisionRequirementsVersionProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(namespaceProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceProp);
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
    return false;
  }

  public DecisionRequirementsRecord setChecksum(final DirectBuffer checksum) {
    checksumProp.setValue(checksum);
    return this;
  }

  public DecisionRequirementsRecord setResourceName(final String resourceName) {
    resourceNameProp.setValue(resourceName);
    return this;
  }

  public DecisionRequirementsRecord setNamespace(final String namespace) {
    namespaceProp.setValue(namespace);
    return this;
  }

  public DecisionRequirementsRecord setDecisionRequirementsKey(final long decisionRequirementsKey) {
    decisionRequirementsKeyProp.setValue(decisionRequirementsKey);
    return this;
  }

  public DecisionRequirementsRecord setDecisionRequirementsVersion(
      final int decisionRequirementsVersion) {
    decisionRequirementsVersionProp.setValue(decisionRequirementsVersion);
    return this;
  }

  public DecisionRequirementsRecord setDecisionRequirementsName(
      final String decisionRequirementsName) {
    decisionRequirementsNameProp.setValue(decisionRequirementsName);
    return this;
  }

  public DecisionRequirementsRecord setDecisionRequirementsId(final String decisionRequirementsId) {
    decisionRequirementsIdProp.setValue(decisionRequirementsId);
    return this;
  }

  @Override
  public byte[] getResource() {
    return bufferAsArray(resourceProp.getValue());
  }

  public DecisionRequirementsRecord setResource(final DirectBuffer resource) {
    resourceProp.setValue(resource);
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
  public DirectBuffer getResourceBuffer() {
    return resourceProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getChecksumBuffer() {
    return checksumProp.getValue();
  }
}

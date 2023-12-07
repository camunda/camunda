/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import org.agrona.DirectBuffer;

public final class PersistedDecisionRequirements extends UnpackedObject implements DbValue {

  private final StringProperty decisionRequirementsIdProp =
      new StringProperty("decisionRequirementsId");
  private final StringProperty decisionRequirementsNameProp =
      new StringProperty("decisionRequirementsName");
  private final IntegerProperty decisionRequirementsVersionProp =
      new IntegerProperty("decisionRequirementsVersion");
  private final LongProperty decisionRequirementsKeyProp =
      new LongProperty("decisionRequirementsKey");

  private final StringProperty resourceNameProp = new StringProperty("resourceName");
  private final BinaryProperty checksumProp = new BinaryProperty("checksum");
  private final BinaryProperty resourceProp = new BinaryProperty("resource");

  public PersistedDecisionRequirements() {
    super(7);
    declareProperty(decisionRequirementsIdProp)
        .declareProperty(decisionRequirementsNameProp)
        .declareProperty(decisionRequirementsVersionProp)
        .declareProperty(decisionRequirementsKeyProp)
        .declareProperty(resourceNameProp)
        .declareProperty(checksumProp)
        .declareProperty(resourceProp);
  }

  public void wrap(final DecisionRequirementsRecord record) {
    decisionRequirementsIdProp.setValue(record.getDecisionRequirementsIdBuffer());
    decisionRequirementsNameProp.setValue(record.getDecisionRequirementsNameBuffer());
    decisionRequirementsVersionProp.setValue(record.getDecisionRequirementsVersion());
    decisionRequirementsKeyProp.setValue(record.getDecisionRequirementsKey());
    resourceNameProp.setValue(record.getResourceNameBuffer());
    checksumProp.setValue(record.getChecksumBuffer());
    resourceProp.setValue(record.getResourceBuffer());
  }

  public PersistedDecisionRequirements copy() {
    final var copy = new PersistedDecisionRequirements();
    copy.decisionRequirementsIdProp.setValue(getDecisionRequirementsId());
    copy.decisionRequirementsNameProp.setValue(getDecisionRequirementsName());
    copy.decisionRequirementsVersionProp.setValue(getDecisionRequirementsVersion());
    copy.decisionRequirementsKeyProp.setValue(getDecisionRequirementsKey());
    copy.resourceNameProp.setValue(getResourceName());
    copy.checksumProp.setValue(getChecksum());
    copy.resourceProp.setValue(getResource());
    return copy;
  }

  public DirectBuffer getDecisionRequirementsId() {
    return decisionRequirementsIdProp.getValue();
  }

  public DirectBuffer getDecisionRequirementsName() {
    return decisionRequirementsNameProp.getValue();
  }

  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersionProp.getValue();
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKeyProp.getValue();
  }

  public DirectBuffer getResourceName() {
    return resourceNameProp.getValue();
  }

  public DirectBuffer getChecksum() {
    return checksumProp.getValue();
  }

  public DirectBuffer getResource() {
    return resourceProp.getValue();
  }
}

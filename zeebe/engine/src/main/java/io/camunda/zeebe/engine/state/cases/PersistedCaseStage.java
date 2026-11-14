/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.cases;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import org.jspecify.annotations.NullMarked;

/**
 * A stage of a case, stored under the key of the case and the element ID of the stage. Both are
 * part of the key, so the value holds only the status.
 */
@NullMarked
public final class PersistedCaseStage extends UnpackedObject implements DbValue {

  private final EnumProperty<CaseStageStatus> statusProp =
      new EnumProperty<>("status", CaseStageStatus.class, CaseStageStatus.NOT_STARTED);

  public PersistedCaseStage() {
    super(1);
    declareProperty(statusProp);
  }

  public CaseStageStatus getStatus() {
    return requireNonNull(statusProp.getValue());
  }

  public PersistedCaseStage setStatus(final CaseStageStatus status) {
    statusProp.setValue(status);
    return this;
  }
}

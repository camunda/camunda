/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scaling;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.RedistributionRecordValue;

public class RedistributionRecord extends UnifiedRecordValue implements RedistributionRecordValue {
  private final IntegerProperty stage = new IntegerProperty("stage", -1);
  private final ObjectProperty<RedistributionProgress> progress =
      new ObjectProperty<>("progress", new RedistributionProgress());

  public RedistributionRecord() {
    super(2);
    declareProperty(stage);
    declareProperty(progress);
  }

  @Override
  public int getStage() {
    return stage.getValue();
  }

  public RedistributionRecord setStage(final int stage) {
    this.stage.setValue(stage);
    return this;
  }

  @Override
  public RedistributionProgress getProgress() {
    return progress.getValue();
  }

  public RedistributionRecord setProgress(final RedistributionProgress progress) {
    this.progress.getValue().copyFrom(progress);
    return this;
  }
}

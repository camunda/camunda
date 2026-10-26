/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.probe;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.probe.ProbeRecord;

public final class ActivatedProbeImpl extends UnpackedObject implements ActivatedProbe {

  private final LongProperty probeKeyProp = new LongProperty("key", -1);
  private final ObjectProperty<ProbeRecord> probeRecordProp =
      new ObjectProperty<>("record", new ProbeRecord());

  public ActivatedProbeImpl() {
    super(2);
    declareProperty(probeKeyProp).declareProperty(probeRecordProp);
  }

  @Override
  public long probeKey() {
    return probeKeyProp.getValue();
  }

  @Override
  public ProbeRecord probeRecord() {
    return probeRecordProp.getValue();
  }

  public ActivatedProbeImpl setProbeKey(final long probeKey) {
    probeKeyProp.setValue(probeKey);
    return this;
  }

  public ActivatedProbeImpl setRecord(final ProbeRecord probeRecord) {
    probeRecordProp.getValue().copyFrom(probeRecord);
    return this;
  }
}

/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class Incident extends MsgpackBufferValue<Incident> implements DbValue {

  final IncidentRecord incidentRecord = new IncidentRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public IncidentRecord getRecord() {
    return incidentRecord;
  }

  public void setRecord(final IncidentRecord record) {
    incidentRecord.wrap(record);
  }

  @Override
  protected MsgpackCodec<Incident> codec() {
    return IncidentCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<Incident> ops() {
    return IncidentOps.INSTANCE;
  }
}

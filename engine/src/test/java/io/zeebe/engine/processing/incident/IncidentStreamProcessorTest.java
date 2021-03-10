/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.state.mutable.MutableIncidentState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.IncidentIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class IncidentStreamProcessorTest {

  public final StreamProcessorRule envRule = new StreamProcessorRule();
  public final IncidentStreamProcessorRule streamProcessorRule =
      new IncidentStreamProcessorRule(envRule);

  @Rule public RuleChain chain = RuleChain.outerRule(envRule).around(streamProcessorRule);

  @Test
  public void shouldNotCreateIncidentIfNoFailedJob() {
    // given
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setJobKey(1);

    // when
    envRule.writeCommand(IncidentIntent.CREATE, incidentRecord); // trigger incident creation

    // then
    streamProcessorRule.awaitIncidentRejection(IncidentIntent.CREATE);

    final List<Record<IncidentRecord>> incidentEvents =
        envRule.events().onlyIncidentRecords().collect(Collectors.toList());
    assertThat(incidentEvents)
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, IncidentIntent.CREATE),
            tuple(RecordType.COMMAND_REJECTION, IncidentIntent.CREATE));
  }

  @Test
  public void shouldNotCreateIncidentIfNoFailedToken() {
    // given
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setElementInstanceKey(2);

    // when
    envRule.writeCommand(IncidentIntent.CREATE, incidentRecord); // trigger incident creation

    // then
    streamProcessorRule.awaitIncidentRejection(IncidentIntent.CREATE);

    final List<Record<IncidentRecord>> incidentEvents =
        envRule.events().onlyIncidentRecords().collect(Collectors.toList());
    assertThat(incidentEvents)
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, IncidentIntent.CREATE),
            tuple(RecordType.COMMAND_REJECTION, IncidentIntent.CREATE));
  }

  @Test
  public void shouldNotResolveIfNoIncident() {
    // given
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setElementInstanceKey(2);

    // when
    envRule.writeCommand(IncidentIntent.RESOLVE, incidentRecord);

    // then
    streamProcessorRule.awaitIncidentRejection(IncidentIntent.RESOLVE);

    final List<Record<IncidentRecord>> incidentEvents =
        envRule.events().onlyIncidentRecords().collect(Collectors.toList());
    assertThat(incidentEvents)
        .extracting(Record::getRecordType, Record::getIntent)
        .containsExactly(
            tuple(RecordType.COMMAND, IncidentIntent.RESOLVE),
            tuple(RecordType.COMMAND_REJECTION, IncidentIntent.RESOLVE));
  }

  @Test
  public void shouldRemoveIncidentFromStateOnResolved() {
    // given
    final MutableIncidentState incidentState =
        streamProcessorRule.getZeebeState().getIncidentState();
    final IncidentRecord incidentRecord = new IncidentRecord();
    incidentRecord.setElementInstanceKey(2);
    incidentState.createIncident(1, incidentRecord);

    // when
    envRule.writeCommand(1, IncidentIntent.RESOLVE, incidentRecord);

    // then
    streamProcessorRule.awaitIncidentInState(IncidentIntent.RESOLVED);
    final IncidentRecord persistedIncident = incidentState.getIncidentRecord(1);
    assertThat(persistedIncident).isNull();
  }
}

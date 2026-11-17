/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class IncidentSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectIncidentResolveWhileSuspended() {
    // given
    final String jobType = Strings.newRandomValidBpmnId();
    final String processId = Strings.newRandomValidBpmnId();
    final Record<JobRecordValue> job = ENGINE.createJob(jobType, processId);
    final long processInstanceKey = job.getValue().getProcessInstanceKey();

    ENGINE.job().withKey(job.getKey()).withRetries(0).fail();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when — resolve(username) carries request metadata, so isInternalCommand() is false and the
    // gate classifies it as REJECT
    final Record<IncidentRecordValue> rejection =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incident.getKey())
            .expectRejection()
            .resolve("some-user");

    // then
    Assertions.assertThat(rejection)
        .hasIntent(IncidentIntent.RESOLVE)
        .hasRejectionType(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason())
        .contains("process instance with key '" + processInstanceKey + "'");
  }
}

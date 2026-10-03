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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies the engine declares protection modes on incidents whose error message may reveal a
 * variable matching the configured sensitive-variable patterns (default {@code sensitive_.*}, mode
 * {@code REDACT}), and masks the sensitive values in their messages.
 */
public class IncidentProtectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String CONDITION_PROCESS = "sensitive-condition";
  private static final String JOB_PROCESS = "sensitive-job";
  private static final String JOB_TYPE = "sensitive-job-type";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void deploy() {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(CONDITION_PROCESS)
                .startEvent()
                .exclusiveGateway()
                .sequenceFlowId("high")
                .conditionExpression("sensitive_salary > 100000")
                .endEvent()
                .moveToLastGateway()
                .sequenceFlowId("plain")
                .conditionExpression("amount > 100000")
                .endEvent()
                .done())
        .withXmlResource(
            Bpmn.createExecutableProcess(JOB_PROCESS)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldProtectIncidentOfExpressionReadingSensitiveVariable() {
    // when -- a string where a number is expected: the FEEL warning quotes the value
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(CONDITION_PROCESS)
            .withVariables(Map.of("sensitive_salary", "98765", "amount", 1))
            .create();

    // then -- the engine masks the value before it writes the record
    final IncidentRecordValue incident = incidentOf(processInstanceKey);
    assertThat(incident.getErrorMessage()).doesNotContain("98765").contains("[REDACTED]");
    assertThat(incident.getProtectionModes()).containsExactly(ProtectionMode.REDACT);
  }

  @Test
  public void shouldMaskOnlyTheValueInTheMessageOfProtectedIncident() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(JOB_PROCESS)
            .withVariables(Map.of("sensitive_salary", 98765))
            .create();

    // when -- the worker reports the variable by name together with its value
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withRetries(0)
        .withErrorMessage("sensitive_salary 98765 Illegal Argument, limit 987650")
        .fail();

    // then -- the name and the unrelated number survive, the value does not
    final var incident = incidentOf(processInstanceKey);
    assertThat(incident.getErrorMessage())
        .isEqualTo("sensitive_salary \"[REDACTED]\" Illegal Argument, limit 987650");
  }

  @Test
  public void shouldNeverExposeTheValueInTheMessage() {
    // when -- the message names the variable; FEEL may quote the value
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(CONDITION_PROCESS)
            .withVariables(Map.of("sensitive_salary", "text", "amount", 1))
            .create();

    // then
    final var incident = incidentOf(processInstanceKey);
    assertThat(incident.getProtectionModes()).containsExactly(ProtectionMode.REDACT);
    assertThat(incident.getErrorMessage()).doesNotContain("\"text\"");
  }

  @Test
  public void shouldNotProtectIncidentUnrelatedToSensitiveVariables() {
    // when -- the failing condition only reads a plain variable; the sensitive value is chosen so
    // it does not occur in the message, which a short value like 1 would (in "100000")
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(CONDITION_PROCESS)
            .withVariables(Map.of("sensitive_salary", 42, "amount", "not-a-number"))
            .create();

    // then
    assertThat(incidentOf(processInstanceKey).getProtectionModes()).isEmpty();
  }

  @Test
  public void shouldProtectIncidentWhoseWorkerMessageContainsSensitiveValue() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(JOB_PROCESS)
            .withVariables(Map.of("sensitive_ssn", "123-45-6789"))
            .create();

    // when -- the worker's own text names no variable, only the value it read
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withRetries(0)
        .withErrorMessage("Customer 123-45-6789 not found in CRM")
        .fail();

    // then
    assertThat(incidentOf(processInstanceKey).getProtectionModes())
        .containsExactly(ProtectionMode.REDACT);
  }

  @Test
  public void shouldNotProtectWorkerMessageWithoutSensitiveValue() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(JOB_PROCESS)
            .withVariables(Map.of("sensitive_ssn", "123-45-6789"))
            .create();

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(JOB_TYPE)
        .withRetries(0)
        .withErrorMessage("CRM unavailable")
        .fail();

    // then
    assertThat(incidentOf(processInstanceKey).getProtectionModes()).isEmpty();
  }

  private static IncidentRecordValue incidentOf(final long processInstanceKey) {
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getValue();
  }
}

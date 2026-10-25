/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.consent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.SecretStoreRegistries;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;
import io.camunda.zeebe.protocol.record.value.ConsentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * A consent belongs to no process instance, but the jobs that wait on it sit on every partition, so
 * a grant and its revocation reach every partition, in order.
 */
public final class ConsentDistributionTest {

  private static final String JOB_TYPE = "hibob-region-update";
  private static final String PROCESS_ID = "employee-region-change";

  @Rule
  public final EngineRule engine =
      EngineRule.multiplePartition(3)
          .withSecretStoreRegistry(SecretStoreRegistries.resolveAll("service-user-token"));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Before
  public void setUp() {
    engine
        .clusterVariables()
        .withName(ConsentConnections.VARIABLE_PREFIX + "hibob")
        .setGlobalScope()
        .withValue(
            Map.of(
                "id",
                "hibob",
                "operations",
                List.of(Map.of("id", "region.update", "consent", "required", "scope", "either"))))
        .create();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "change",
                    t ->
                        t.zeebeJobType(JOB_TYPE)
                            .zeebeConsent("hibob", "region.update")
                            .zeebeConsentChangeExpression("{region: newRegion}"))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldReleaseAJobParkedOnAnotherPartitionWhenTheGrantArrives() {
    // given a job on partition 2 waits for Alice
    final long processInstanceKey = startOnPartition(2, "DACH");
    awaitRequest(processInstanceKey);

    // when Alice grants consent, through the deployment partition
    engine.consent().grant("hibob", "region.update", "alice");

    // then the grant reaches partition 2 and releases the job there
    assertThat(
            RecordingExporter.records()
                .withPartitionId(2)
                .withValueType(ValueType.CONSENT)
                .withIntent(ConsentIntent.RELEASED)
                .exists())
        .isTrue();
    assertThat(engine.jobs().withType(JOB_TYPE).onPartition(2).activate().getValue().getJobs())
        .hasSize(1);
  }

  @Test
  public void shouldStopHandOutsOnEveryPartitionAfterARevocation() {
    // given
    final var grant = engine.consent().grant("hibob", "region.update", "alice");
    for (final int partition : List.of(2, 3)) {
      RecordingExporter.records()
          .withPartitionId(partition)
          .withValueType(ValueType.CONSENT)
          .withIntent(ConsentIntent.GRANTED)
          .getFirst();
    }

    // when
    engine.consent().revoke(grant.getKey(), "", "alice");

    // then every partition applies the revocation after the grant, and a new job parks again
    for (final int partition : List.of(2, 3)) {
      final var records =
          RecordingExporter.records()
              .withPartitionId(partition)
              .withValueType(ValueType.CONSENT)
              .filter(
                  r ->
                      r.getIntent() == ConsentIntent.GRANTED
                          || r.getIntent() == ConsentIntent.REVOKED)
              .limit(2)
              .map(r -> r.getIntent())
              .toList();
      assertThat(records).containsExactly(ConsentIntent.GRANTED, ConsentIntent.REVOKED);
    }
    final long processInstanceKey = startOnPartition(3, "APAC");
    assertThat(awaitRequest(processInstanceKey).getSubject()).isEqualTo("alice");
    assertThat(engine.jobs().withType(JOB_TYPE).onPartition(3).activate().getValue().getJobs())
        .isEmpty();
  }

  private long startOnPartition(final int partition, final String region) {
    return engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .onPartition(partition)
        .withVariables(Map.of("newRegion", region))
        .create("alice");
  }

  private static ConsentRecordValue awaitRequest(final long processInstanceKey) {
    return (ConsentRecordValue)
        RecordingExporter.records()
            .withValueType(ValueType.CONSENT)
            .withIntent(ConsentIntent.REQUESTED)
            .filter(
                r ->
                    ((ConsentRecordValue) r.getValue()).getProcessInstanceKey()
                        == processInstanceKey)
            .getFirst()
            .getValue();
  }
}

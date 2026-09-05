/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UsageMetricIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableForm;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class AnalyticsRecordFilterTest {

  private static final int TEST_PARTITION_ID = 1;
  private static final int REMOTE_PARTITION = 3;
  private static final int CLUSTER_PARTITION_COUNT = 5;
  private static final ProtocolFactory FACTORY = new ProtocolFactory();

  private final AnalyticsRecordFilter filter =
      new AnalyticsRecordFilter(
          Set.of(
              ValueType.PROCESS_INSTANCE_CREATION,
              ValueType.PROCESS_INSTANCE,
              ValueType.USAGE_METRIC),
          Set.of(
              ProcessInstanceCreationIntent.CREATED,
              ProcessInstanceIntent.ELEMENT_ACTIVATED,
              UsageMetricIntent.EXPORTED),
          TEST_PARTITION_ID);

  @Test
  void shouldAcceptEventRecordType() {
    assertThat(filter.acceptType(RecordType.EVENT)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = RecordType.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"EVENT"})
  void shouldRejectNonEventRecordType(final RecordType type) {
    assertThat(filter.acceptType(type)).isFalse();
  }

  @Test
  void shouldAcceptAllRegisteredValueTypes() {
    assertThat(filter.acceptValue(ValueType.PROCESS_INSTANCE_CREATION)).isTrue();
    assertThat(filter.acceptValue(ValueType.PROCESS_INSTANCE)).isTrue();
    assertThat(filter.acceptValue(ValueType.USAGE_METRIC)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ValueType.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"PROCESS_INSTANCE_CREATION", "PROCESS_INSTANCE", "USAGE_METRIC"})
  void shouldRejectUnregisteredValueType(final ValueType type) {
    assertThat(filter.acceptValue(type)).isFalse();
  }

  @Test
  void shouldAcceptAllRegisteredIntents() {
    assertThat(filter.acceptIntent(ProcessInstanceCreationIntent.CREATED)).isTrue();
    assertThat(filter.acceptIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)).isTrue();
    assertThat(filter.acceptIntent(UsageMetricIntent.EXPORTED)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("unregisteredIntents")
  void shouldRejectUnregisteredIntent(final Intent intent) {
    assertThat(filter.acceptIntent(intent)).isFalse();
  }

  private static Stream<Intent> unregisteredIntents() {
    return Stream.of(
        ProcessInstanceCreationIntent.CREATE,
        ProcessInstanceIntent.ELEMENT_COMPLETING,
        DeploymentIntent.CREATED);
  }

  @Test
  void shouldAcceptRecordFromLocalPartition() {
    // given
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withKey(Protocol.encodePartitionId(TEST_PARTITION_ID, 1))
                    .withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED));

    // when / then
    assertThat(filter.acceptRecord(record)).isTrue();
  }

  @Test
  void shouldRejectRecordFromRemotePartition() {
    // given — key encodes partition 2, but exporter runs on partition 1
    final int remotePartition = 2;
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withKey(Protocol.encodePartitionId(remotePartition, 1))
                    .withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED));

    // when / then
    assertThat(filter.acceptRecord(record)).isFalse();
  }

  /**
   * The engine's {@code ResourceDeletionDeleteProcessor} runs on every partition and mints the
   * DELETED event key locally, so only the definition key in the value identifies the originating
   * partition. Exactly one partition of the cluster may emit the event; anything else is an N-fold
   * over-count.
   */
  @ParameterizedTest
  @MethodSource("deletedDefinitionsOwnedByRemotePartition")
  void shouldAcceptDeletedDefinitionOnOriginatingPartitionOnly(final Record<?> record) {
    // given — the definition was minted on REMOTE_PARTITION, the event key on the local one
    final var acceptingPartitions =
        IntStream.rangeClosed(1, CLUSTER_PARTITION_COUNT)
            .filter(partition -> definitionFilter(partition).acceptRecord(record))
            .boxed()
            .toList();

    // when / then
    assertThat(acceptingPartitions).containsExactly(REMOTE_PARTITION);
  }

  private static Stream<Named<Record<?>>> deletedDefinitionsOwnedByRemotePartition() {
    final long definitionKey = Protocol.encodePartitionId(REMOTE_PARTITION, 7);
    return Stream.of(
        Named.of("PROCESS", deletedProcess(definitionKey)),
        Named.of("DECISION", deletedDecision(definitionKey)),
        Named.of("FORM", deletedForm(definitionKey)));
  }

  @Test
  void shouldAcceptCreatedDefinitionFromLocalPartition() {
    // given — for CREATED the event key and the value's definition key are the same key
    final long definitionKey = Protocol.encodePartitionId(TEST_PARTITION_ID, 7);
    final var record =
        FACTORY.generateRecord(
            ValueType.PROCESS,
            r ->
                r.withKey(definitionKey)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(ProcessIntent.CREATED)
                    .withValue(process(definitionKey)));

    // when / then
    assertThat(definitionFilter(TEST_PARTITION_ID).acceptRecord(record)).isTrue();
  }

  private static AnalyticsRecordFilter definitionFilter(final int partitionId) {
    return new AnalyticsRecordFilter(
        Set.of(ValueType.PROCESS, ValueType.DECISION, ValueType.FORM),
        Set.of(
            ProcessIntent.CREATED,
            ProcessIntent.DELETED,
            DecisionIntent.DELETED,
            FormIntent.DELETED),
        partitionId);
  }

  private static Record<?> deletedProcess(final long processDefinitionKey) {
    return FACTORY.generateRecord(
        ValueType.PROCESS,
        r ->
            r.withKey(locallyMintedEventKey())
                .withRecordType(RecordType.EVENT)
                .withIntent(ProcessIntent.DELETED)
                .withValue(process(processDefinitionKey)));
  }

  private static Record<?> deletedDecision(final long decisionKey) {
    return FACTORY.generateRecord(
        ValueType.DECISION,
        r ->
            r.withKey(locallyMintedEventKey())
                .withRecordType(RecordType.EVENT)
                .withIntent(DecisionIntent.DELETED)
                .withValue(
                    ImmutableDecisionRecordValue.builder()
                        .withDecisionId("credit-scoring")
                        .withDecisionKey(decisionKey)
                        .withDecisionName("Credit scoring")
                        .withVersion(3)
                        .withTenantId("acme")
                        .build()));
  }

  private static Record<?> deletedForm(final long formKey) {
    return FACTORY.generateRecord(
        ValueType.FORM,
        r ->
            r.withKey(locallyMintedEventKey())
                .withRecordType(RecordType.EVENT)
                .withIntent(FormIntent.DELETED)
                .withValue(
                    ImmutableForm.builder()
                        .withFormId("customer-onboarding")
                        .withFormKey(formKey)
                        .withVersion(3)
                        .withResourceName("onboarding.form")
                        .withResource("{\"components\":[]}".getBytes(StandardCharsets.UTF_8))
                        .withTenantId("acme")
                        .build()));
  }

  private static ImmutableProcess process(final long processDefinitionKey) {
    return ImmutableProcess.builder()
        .withBpmnProcessId("order-process")
        .withProcessDefinitionKey(processDefinitionKey)
        .withVersion(3)
        .withResourceName("order-process.bpmn")
        .withResource("<definitions/>".getBytes(StandardCharsets.UTF_8))
        .withTenantId("acme")
        .build();
  }

  /** Every partition mints the same event key for its own copy of the DELETED follow-up event. */
  private static long locallyMintedEventKey() {
    return Protocol.encodePartitionId(TEST_PARTITION_ID, 99);
  }
}

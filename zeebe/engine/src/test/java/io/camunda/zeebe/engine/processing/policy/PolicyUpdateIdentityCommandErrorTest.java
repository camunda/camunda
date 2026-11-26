/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.engine.processing.identity.AbstractIdentityCommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.util.Records;
import io.camunda.zeebe.engine.util.TestStreams;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.scheduler.testing.ActorSchedulerRule;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.records.CopiedRecords;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.test.util.AutoCloseableRule;
import java.time.InstantSource;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.agrona.CloseHelper;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;

/**
 * An identity processor that throws while applying a policy update fails the update, so the update
 * does not stay open and block the updates after it. The engine has no identity processor that
 * throws on purpose, so this test registers the policy processors next to a role processor that
 * always throws.
 */
public final class PolicyUpdateIdentityCommandErrorTest {

  private static final String STREAM_NAME = "policy";

  private final TemporaryFolder tempFolder = new TemporaryFolder();
  private final AutoCloseableRule closeables = new AutoCloseableRule();
  private final ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();

  @Rule
  public final RuleChain ruleChain =
      RuleChain.outerRule(tempFolder).around(actorSchedulerRule).around(closeables);

  private TestStreams streams;
  private StreamProcessor streamProcessor;
  private RuntimeException error = new RuntimeException("expected");

  @Before
  public void setUp() {
    streams =
        new TestStreams(tempFolder, closeables, actorSchedulerRule.get(), InstantSource.system());
    streams.createLogStream(STREAM_NAME, Protocol.DEPLOYMENT_PARTITION);
    streamProcessor =
        streams.startStreamProcessor(
            STREAM_NAME,
            DefaultZeebeDbFactory.defaultFactory(),
            context -> {
              final var state = context.getProcessingState();
              final var policyUpdateState = state.getPolicyUpdateState();
              final var writers = context.getWriters();
              return TypedRecordProcessors.processors()
                  .onCommand(
                      ValueType.POLICY_UPDATE,
                      PolicyUpdateIntent.APPEND_CHUNK,
                      new PolicyUpdateAppendChunkProcessor(
                          policyUpdateState, state.getKeyGenerator(), writers))
                  .onCommand(
                      ValueType.POLICY_UPDATE,
                      PolicyUpdateIntent.APPLY_CHUNK,
                      new PolicyUpdateApplyChunkProcessor(
                          policyUpdateState,
                          new PolicyUpdateBehavior(policyUpdateState, writers.state()),
                          new PolicyUpdateCommandMapper(state),
                          state.getKeyGenerator(),
                          writers))
                  .onCommand(
                      ValueType.ROLE,
                      RoleIntent.CREATE,
                      new AbstractIdentityCommandProcessor<RoleRecord>(
                          new PolicyUpdateBehavior(policyUpdateState, writers.state()), writers) {
                        @Override
                        protected void processNewIdentityCommand(
                            final TypedRecord<RoleRecord> command) {
                          throw error;
                        }

                        @Override
                        public void processDistributedCommand(
                            final TypedRecord<RoleRecord> command) {}
                      });
            });
  }

  @After
  public void tearDown() {
    CloseHelper.close(streamProcessor);
  }

  @Test
  public void shouldFailTheUpdateWhenOneOfItsCommandsThrows() {
    // given
    final var roleId = "role-" + uid();
    final var update =
        chunk().setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId)));

    // when
    appendChunk(update);

    // then
    final var failed =
        await(
            record -> Records.isEvent(record, ValueType.POLICY_UPDATE, PolicyUpdateIntent.FAILED),
            PolicyUpdateRecordValue.class);
    assertThat(failed.getValue().getUpdateId()).isEqualTo(update.getUpdateId());
    assertThat(failed.getValue().getErrorMessage())
        .contains(update.getUpdateId(), "ROLE.CREATE", "unexpected error", "expected");

    final var rejection =
        await(
            record -> Records.isRejection(record, ValueType.ROLE, RoleIntent.CREATE),
            RoleRecordValue.class);
    assertThat(rejection.getValue().getRoleId()).isEqualTo(roleId);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.PROCESSING_ERROR);
  }

  @Test
  public void shouldLeaveTheRecordOutOfTheFailureWhenACommandExceedsTheBatchSize() {
    // given
    error =
        new ExceededBatchRecordSizeException(mock(RecordBatchEntry.class), 4_000_000, 1, 4_000_000);
    final var update =
        chunk().setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId("role-" + uid())));

    // when
    appendChunk(update);

    // then
    final var failed =
        await(
            record -> Records.isEvent(record, ValueType.POLICY_UPDATE, PolicyUpdateIntent.FAILED),
            PolicyUpdateRecordValue.class);
    assertThat(failed.getValue().getErrorMessage())
        .contains(update.getUpdateId(), "ROLE.CREATE", "exceeded the maximum batch size")
        .doesNotContain(error.getMessage());

    final var rejection =
        await(
            record -> Records.isRejection(record, ValueType.ROLE, RoleIntent.CREATE),
            RoleRecordValue.class);
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.EXCEEDED_BATCH_RECORD_SIZE);
  }

  @Test
  public void shouldApplyTheNextUpdateAfterAnUpdateFailedOnAnError() {
    // given
    appendChunk(chunk().setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(uid()))));
    await(
        record -> Records.isEvent(record, ValueType.POLICY_UPDATE, PolicyUpdateIntent.FAILED),
        PolicyUpdateRecordValue.class);

    // when
    final var next = chunk();
    appendChunk(next);

    // then
    final var applied =
        await(
            record ->
                Records.isEvent(record, ValueType.POLICY_UPDATE, PolicyUpdateIntent.CHUNK_APPLIED)
                    && hasUpdateId(record, next.getUpdateId()),
            PolicyUpdateRecordValue.class);
    assertThat(applied.getValue().getUpdateId()).isEqualTo(next.getUpdateId());
  }

  private void appendChunk(final PolicyUpdateRecord chunk) {
    streams
        .newRecord(STREAM_NAME)
        .event(chunk)
        .recordType(RecordType.COMMAND)
        .intent(PolicyUpdateIntent.APPEND_CHUNK)
        .key(-1)
        .write();
  }

  @SuppressWarnings("unchecked")
  private <T extends RecordValue> Record<T> await(
      final Predicate<LoggedEvent> filter, final Class<T> type) {
    return Awaitility.await()
        .until(
            () ->
                streams
                    .events(STREAM_NAME)
                    .filter(filter)
                    .findFirst()
                    .map(
                        event ->
                            (Record<T>)
                                (Record<?>)
                                    CopiedRecords.createCopiedRecord(
                                        Protocol.DEPLOYMENT_PARTITION, event)),
            Optional::isPresent)
        .orElseThrow();
  }

  private static boolean hasUpdateId(final LoggedEvent event, final String updateId) {
    final var record = CopiedRecords.createCopiedRecord(Protocol.DEPLOYMENT_PARTITION, event);
    return ((PolicyUpdateRecordValue) record.getValue()).getUpdateId().equals(updateId);
  }
}

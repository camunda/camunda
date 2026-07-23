/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.metrics.CompiledEntityMetrics;
import io.camunda.analytics.lake.metrics.EntityMetrics;
import io.camunda.analytics.lake.metrics.PollFedRider;
import io.camunda.analytics.lake.objects.CompiledObjectType;
import io.camunda.analytics.lake.objects.CompiledObjectTypes;
import io.camunda.analytics.lake.objects.ObjectTypes;
import io.camunda.analytics.lake.sink.BatchEncoder;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.DataFileResult;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.sink.SortedRun;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.state.TranslatorState.BirthQualifier;
import io.camunda.analytics.lake.state.TranslatorState.LifecycleStatus;
import io.camunda.analytics.lake.state.TranslatorState.ObjectLifecycle;
import io.camunda.analytics.lake.translate.RawTableSchemas.ObjectLifecycleColumns;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.apache.iceberg.Metrics;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LakeTranslator}'s object lifecycle capture (see its class javadoc's "Object
 * lifecycle capture" section): birth-once semantics (incl. replay-with-existing- accumulator and
 * late-sighting-after-tombstone), the {@code nSightings} fold, the {@code objects_born} poll-fed
 * counter, closing emission (outcome mapping, duration, double-close suppression), the
 * backpressure-propagation ordering that makes a retry safe, and the two feature-off cases ({@code
 * objectTypes}/{@code objectLifecycleAppender} unwired).
 */
class LakeTranslatorObjectLifecycleTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "dispute-handling-test-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 1L;
  private static final int ZEEBE_PARTITION = 1;

  private static final CompiledObjectType DISPUTE_CLOSES_ON_PROCESS =
      ObjectTypes.declare("dispute")
          .identifiedBy(ObjectTypes.variable("correlationKey"))
          .closes(ObjectTypes.onProcessCompletion(PROCESS_ID))
          .build();

  private static final CompiledObjectType CUSTOMER_NEVER_CLOSES =
      ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build();

  private static final CompiledObjectTypes OBJECT_TYPES =
      CompiledObjectTypes.of(DISPUTE_CLOSES_ON_PROCESS, CUSTOMER_NEVER_CLOSES);

  // ---- birth ------------------------------------------------------------------------------

  @Test
  void shouldCreateAnOpenAccumulatorOnFirstSighting() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // then
    final ObjectLifecycle lifecycle = state.getObjectLifecycle("dispute", "disp-1");
    assertThat(lifecycle).isNotNull();
    assertThat(lifecycle.status()).isEqualTo(LifecycleStatus.OPEN);
    assertThat(lifecycle.birthTsMs()).isEqualTo(200L);
    assertThat(lifecycle.birthQualifier()).isEqualTo(BirthQualifier.FIRST_SIGHTING);
    assertThat(lifecycle.nSightings()).isEqualTo(1);
  }

  @Test
  void shouldNotRebirthOnAReplayedDuplicateSighting() {
    // given: the identical (type, id, instance, scope) sighted twice -- a duplicate from CF-7's
    // own perspective (see LakeTranslator#recordObjectSightingForRelations)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when: a second, identical sighting
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 300L, 3L));

    // then: birth timestamp untouched, nSightings NOT recounted
    final ObjectLifecycle lifecycle = state.getObjectLifecycle("dispute", "disp-1");
    assertThat(lifecycle.birthTsMs()).isEqualTo(200L);
    assertThat(lifecycle.nSightings()).isEqualTo(1);
  }

  @Test
  void shouldNotRebirthOnAReplayWithAnAlreadyExistingAccumulator() {
    // given: one translator folds a sighting, durably creating the CF-8 accumulator
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator beforeRestart = newTranslator(state, null, null, OBJECT_TYPES);
    beforeRestart.onRecord(activateRoot(1L, 100L, 1L));
    beforeRestart.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when: a FRESH translator instance over the SAME durable state re-folds the identical
    // record -- this is exactly the replay shape the class javadoc's "Origin-position dedup"
    // section describes: RocksDB state can be durably ahead of the lake's own committed cut
    final LakeTranslator afterRestart = newTranslator(state, null, null, OBJECT_TYPES);
    afterRestart.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // then: the accumulator's presence alone prevented a second birth
    final ObjectLifecycle lifecycle = state.getObjectLifecycle("dispute", "disp-1");
    assertThat(lifecycle.birthTsMs()).isEqualTo(200L);
    assertThat(lifecycle.nSightings()).isEqualTo(1);
  }

  @Test
  void shouldIncrementNSightingsForEachGenuinelyDistinctSightingWhileOpen() {
    // given: the same object sighted at two DIFFERENT scopes of the same instance -- two
    // genuinely distinct CF-7 entries
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 55L, 250L, 3L));

    // then
    assertThat(state.getObjectLifecycle("dispute", "disp-1").nSightings()).isEqualTo(2);
  }

  @Test
  void shouldNotReopenOrRecountATombstonedObjectOnALateSighting() {
    // given: the object is closed (tombstoned) by one instance's completion
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        newTranslator(state, new CapturingRowAppender(), null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));
    translator.onRecord(completeRoot(1L, 300L, 3L));
    final ObjectLifecycle tombstoned = state.getObjectLifecycle("dispute", "disp-1");
    assertThat(tombstoned.status()).isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);

    // when: a DIFFERENT instance later sights the identical object id (cross-instance sighting
    // of the same object is not itself forbidden -- only reopening/recounting is)
    translator.onRecord(activateRoot(2L, 400L, 4L));
    translator.onRecord(variableRecord(2L, "correlationKey", "\"disp-1\"", 2L, 500L, 5L));

    // then: still tombstoned, at the same closedAtMs/nSightings as before -- the late sighting
    // is a documented no-op, not a reopen
    final ObjectLifecycle stillTombstoned = state.getObjectLifecycle("dispute", "disp-1");
    assertThat(stillTombstoned.status()).isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);
    assertThat(stillTombstoned.closedAtMs()).isEqualTo(tombstoned.closedAtMs());
    assertThat(stillTombstoned.nSightings()).isEqualTo(tombstoned.nSightings());
  }

  // ---- objects_born poll-fed counter -------------------------------------------------------

  @Test
  void shouldFoldTheBornCounterExactlyOnceForAnObjectsFirstSighting() {
    // given
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider bornRider = new PollFedRider(objectsBornMetrics(), encoders, 128);
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, bornRider, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));
    bornRider.onPollBoundary();
    bornRider.onWindowClose();

    // then
    final List<Map<String, Object>> rows = encoders.rows("objects_born_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("object_type", "dispute").containsEntry("cnt", 1L);
  }

  @Test
  void shouldNotFoldTheBornCounterAgainOnARepeatSighting() {
    // given
    final RecordingEncoderFactory encoders = new RecordingEncoderFactory();
    final PollFedRider bornRider = new PollFedRider(objectsBornMetrics(), encoders, 128);
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, bornRider, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when: the identical (type, id, instance, scope) sighted again
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 250L, 3L));
    bornRider.onPollBoundary();
    bornRider.onWindowClose();

    // then: still exactly one birth counted
    final List<Map<String, Object>> rows = encoders.rows("objects_born_test_metrics");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0)).containsEntry("cnt", 1L);
  }

  @Test
  void shouldNoOpTheBornCounterFoldWhenRiderUnwired() {
    // given: objectsBornRider == null -- birth accumulator still created, just not counted
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // then: no exception, and the accumulator itself is still there
    assertThat(state.getObjectLifecycle("dispute", "disp-1")).isNotNull();
  }

  // ---- closing ------------------------------------------------------------------------------

  @Test
  void shouldEmitALifecycleRowOnClosingCompletionWithCompletedOutcome() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, lifecycleAppender, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 500L, 3L));

    // then
    assertThat(lifecycleAppender.rows).hasSize(1);
    final Map<Integer, Object> row = lifecycleAppender.rows.get(0);
    assertThat(row.get(ObjectLifecycleColumns.OBJECT_TYPE)).isEqualTo("dispute");
    assertThat(row.get(ObjectLifecycleColumns.OBJECT_ID)).isEqualTo("disp-1");
    assertThat(row.get(ObjectLifecycleColumns.BIRTH_QUALIFIER)).isEqualTo("FIRST_SIGHTING");
    assertThat(row.get(ObjectLifecycleColumns.OUTCOME)).isEqualTo("COMPLETED");
    assertThat(row.get(ObjectLifecycleColumns.N_SIGHTINGS)).isEqualTo(1);
  }

  @Test
  void shouldMapTerminatedFinalStateToTerminatedOutcome() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, lifecycleAppender, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(terminateRoot(1L, 500L, 3L));

    // then
    assertThat(lifecycleAppender.rows.get(0).get(ObjectLifecycleColumns.OUTCOME))
        .isEqualTo("TERMINATED");
  }

  @Test
  void shouldComputeBirthTsClosedAtAndDurationAsMicrosAndMillisRespectively() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, lifecycleAppender, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 700L, 3L));

    // then: millis-to-micros conversion (see LakeTranslator#millisToMicros) applies to both
    // timestamptz columns; duration_ms stays a plain millisecond difference
    final Map<Integer, Object> row = lifecycleAppender.rows.get(0);
    assertThat(row.get(ObjectLifecycleColumns.BIRTH_TS)).isEqualTo(200_000L);
    assertThat(row.get(ObjectLifecycleColumns.CLOSED_AT)).isEqualTo(700_000L);
    assertThat(row.get(ObjectLifecycleColumns.DURATION_MS)).isEqualTo(500L);
  }

  @Test
  void shouldFlipTheAccumulatorToClosedTombstoneAfterClosing() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator =
        newTranslator(state, new CapturingRowAppender(), null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 700L, 3L));

    // then
    final ObjectLifecycle lifecycle = state.getObjectLifecycle("dispute", "disp-1");
    assertThat(lifecycle.status()).isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);
    assertThat(lifecycle.closedAtMs()).isEqualTo(700L);
    assertThat(lifecycle.birthTsMs()).isEqualTo(200L); // unchanged by the flip
  }

  @Test
  void shouldSuppressADoubleCloseWhenTheSameObjectIsSightedAtTwoScopesInOneInstance() {
    // given: the identical (type, id) sighted at two distinct scopes of the SAME completing
    // instance -- two CF-7 entries, one underlying object
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, lifecycleAppender, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 77L, 250L, 3L));

    // when
    translator.onRecord(completeRoot(1L, 500L, 4L));

    // then: only ONE lifecycle row, not two -- the second candidate re-reads the already-
    // tombstoned accumulator and skips (see LakeTranslator's own javadoc on this self-correcting
    // property)
    assertThat(lifecycleAppender.rows).hasSize(1);
  }

  @Test
  void shouldNotEmitALifecycleRowWhenTheProcessIsNotADeclaredClosingProcess() {
    // given: a registry where NOTHING declares a closing rule for this process
    final CompiledObjectType disputeNoClose =
        ObjectTypes.declare("dispute").identifiedBy(ObjectTypes.variable("correlationKey")).build();
    final CompiledObjectTypes noClosingTypes = CompiledObjectTypes.of(disputeNoClose);
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, lifecycleAppender, null, noClosingTypes);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 500L, 3L));

    // then: no row, and the object stays open forever (the default-open case)
    assertThat(lifecycleAppender.rows).isEmpty();
    assertThat(state.getObjectLifecycle("dispute", "disp-1").status())
        .isEqualTo(LifecycleStatus.OPEN);
  }

  @Test
  void shouldNotEmitALifecycleRowForAnObjectTypeThatItselfDeclaresNoClosingRule() {
    // given: "customer" (CUSTOMER_NEVER_CLOSES) sighted on the SAME completing process as
    // "dispute" -- only "dispute" closes on it
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, lifecycleAppender, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 200L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 500L, 3L));

    // then
    assertThat(lifecycleAppender.rows).isEmpty();
    assertThat(state.getObjectLifecycle("customer", "cust-1").status())
        .isEqualTo(LifecycleStatus.OPEN);
  }

  @Test
  void shouldSkipClosingEntirelyWhenObjectLifecycleAppenderUnwired() {
    // given: objectLifecycleAppender == null disables closing-candidate scanning entirely
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(
            state,
            instanceAppender,
            new CapturingRowAppender(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            OBJECT_TYPES,
            null,
            null);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));

    // when
    final boolean consumed = translator.onRecord(completeRoot(1L, 500L, 3L));

    // then: the primary instance row still lands normally, and the accumulator is left OPEN
    // (never flipped -- nothing could safely flip it with no appender to write the fact to)
    assertThat(consumed).isTrue();
    assertThat(instanceAppender.rows).hasSize(1);
    assertThat(state.getObjectLifecycle("dispute", "disp-1").status())
        .isEqualTo(LifecycleStatus.OPEN);
  }

  @Test
  void shouldSkipClosingEntirelyWhenObjectTypesUnwired() {
    // given: objectTypes == null disables every object-fabric hook, lifecycle included
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final CapturingRowAppender lifecycleAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(
            state,
            instanceAppender,
            new CapturingRowAppender(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            lifecycleAppender,
            null);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    final boolean consumed = translator.onRecord(completeRoot(1L, 500L, 2L));

    // then
    assertThat(consumed).isTrue();
    assertThat(instanceAppender.rows).hasSize(1);
    assertThat(lifecycleAppender.rows).isEmpty();
  }

  // ---- backpressure-propagation ordering ---------------------------------------------------

  @Test
  void shouldPropagateBackpressureBeforeAnyMutationAndAllowACleanRetry() {
    // given: the lifecycle appender's begin() fails exactly once
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender lifecycleAppender = new FlakyRowAppender(1);
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(
            state,
            instanceAppender,
            new CapturingRowAppender(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            OBJECT_TYPES,
            lifecycleAppender,
            null);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));
    final ZeebeRecord completion = completeRoot(1L, 500L, 3L);

    // when: the first attempt hits backpressure on the lifecycle fact appender
    final boolean firstAttempt = translator.onRecord(completion);

    // then: propagated as false, and -- unlike every dictionary appender's own absorb path --
    // NOTHING mutated yet: the primary instance row was never even attempted, the instance is
    // still open in state, and the accumulator is still OPEN
    assertThat(firstAttempt).isFalse();
    assertThat(instanceAppender.rows).isEmpty();
    assertThat(state.getInstance(1L)).isNotNull();
    assertThat(state.getObjectLifecycle("dispute", "disp-1").status())
        .isEqualTo(LifecycleStatus.OPEN);

    // when: the caller retries the EXACT SAME record (per onRecord's own contract)
    final boolean secondAttempt = translator.onRecord(completion);

    // then: exactly one lifecycle row and exactly one primary instance row -- no duplicates
    assertThat(secondAttempt).isTrue();
    assertThat(lifecycleAppender.rowsAppended).isEqualTo(1);
    assertThat(instanceAppender.rows).hasSize(1);
    assertThat(state.getObjectLifecycle("dispute", "disp-1").status())
        .isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);
  }

  @Test
  void shouldNotDuplicateAnAlreadyClosedCandidateWhenARetryCoversMultipleCandidates() {
    // given: two INDEPENDENT objects sighted by the same completing instance, but the appender
    // fails on the second begin() call -- the first candidate ("disp-1") succeeds and flips its
    // accumulator before the failure on the second ("disp-2")
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender lifecycleAppender = new FlakyRowAppender(0);
    lifecycleAppender.failOnNthBegin(2);
    final CapturingRowAppender instanceAppender = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(
            state,
            instanceAppender,
            new CapturingRowAppender(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            OBJECT_TYPES,
            lifecycleAppender,
            null);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 1L, 200L, 2L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-2\"", 1L, 260L, 3L));
    final ZeebeRecord completion = completeRoot(1L, 500L, 4L);

    // when: first attempt -- "disp-1" (sighted first, so scanned first) succeeds and is flipped
    // before "disp-2"'s begin() call hits the configured failure
    final boolean firstAttempt = translator.onRecord(completion);

    // then: false (backpressure), but "disp-1" already landed and flipped
    assertThat(firstAttempt).isFalse();
    assertThat(state.getObjectLifecycle("dispute", "disp-1").status())
        .isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);
    assertThat(state.getObjectLifecycle("dispute", "disp-2").status())
        .isEqualTo(LifecycleStatus.OPEN);

    // when: retry
    final boolean secondAttempt = translator.onRecord(completion);

    // then: exactly one row per object -- "disp-1" was NOT re-emitted on retry (its own re-read
    // already showed CLOSED_TOMBSTONE), only "disp-2" (the one that actually failed) was retried
    assertThat(secondAttempt).isTrue();
    assertThat(lifecycleAppender.rowsAppended).isEqualTo(2);
    assertThat(state.getObjectLifecycle("dispute", "disp-2").status())
        .isEqualTo(LifecycleStatus.CLOSED_TOMBSTONE);
  }

  // ---- helpers ----------------------------------------------------------------------------

  private static LakeTranslator newTranslator(
      final TranslatorState state,
      final RowAppender objectLifecycleAppender,
      final PollFedRider objectsBornRider,
      final CompiledObjectTypes objectTypes) {
    return new LakeTranslator(
        state,
        new CapturingRowAppender(),
        new CapturingRowAppender(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        objectTypes,
        objectLifecycleAppender,
        objectsBornRider);
  }

  private static CompiledEntityMetrics objectsBornMetrics() {
    final TableSchema schema =
        new TableSchema(
            "objects_born_test",
            List.of(
                new TableSchema.Column("object_type", ColumnType.STRING_DICT, 1, false, -1, false),
                new TableSchema.Column(
                    "birth_ts",
                    ColumnType.LONG,
                    2,
                    false,
                    -1,
                    true,
                    TableSchema.LogicalType.TIMESTAMPTZ)));
    return EntityMetrics.declare("objects_born_test", schema)
        .dims("object_type")
        .window(Duration.ofMinutes(1), "birth_ts")
        .count()
        .build();
  }

  private static ZeebeRecord activateRoot(
      final long instanceKey, final long timestamp, final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId("")
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withFlowScopeKey(-1L)
            .build();
    return wrapRecord(
        ValueType.PROCESS_INSTANCE,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        instanceKey,
        timestamp,
        position,
        value);
  }

  private static ZeebeRecord completeRoot(
      final long instanceKey, final long timestamp, final long position) {
    return rootFinal(instanceKey, timestamp, position, ProcessInstanceIntent.ELEMENT_COMPLETED);
  }

  private static ZeebeRecord terminateRoot(
      final long instanceKey, final long timestamp, final long position) {
    return rootFinal(instanceKey, timestamp, position, ProcessInstanceIntent.ELEMENT_TERMINATED);
  }

  private static ZeebeRecord rootFinal(
      final long instanceKey,
      final long timestamp,
      final long position,
      final ProcessInstanceIntent intent) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId("")
            .withBpmnElementType(BpmnElementType.PROCESS)
            .withFlowScopeKey(-1L)
            .build();
    return wrapRecord(ValueType.PROCESS_INSTANCE, intent, instanceKey, timestamp, position, value);
  }

  private static ZeebeRecord variableRecord(
      final long processInstanceKey,
      final String name,
      final String valueJson,
      final long scopeKey,
      final long timestamp,
      final long position) {
    final VariableRecordValue value =
        ImmutableVariableRecordValue.builder()
            .withName(name)
            .withValue(valueJson)
            .withScopeKey(scopeKey)
            .withProcessInstanceKey(processInstanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withBpmnProcessId(PROCESS_ID)
            .withTenantId(TENANT_ID)
            .build();
    return wrapRecord(
        ValueType.VARIABLE, VariableIntent.CREATED, processInstanceKey, timestamp, position, value);
  }

  private static <T extends RecordValue> ZeebeRecord wrapRecord(
      final ValueType valueType,
      final Intent intent,
      final long key,
      final long timestamp,
      final long position,
      final T value) {
    final Record<T> record =
        ImmutableRecord.<T>builder()
            .withRecordType(RecordType.EVENT)
            .withValueType(valueType)
            .withIntent(intent)
            .withKey(key)
            .withTimestamp(timestamp)
            .withPartitionId(ZEEBE_PARTITION)
            .withPosition(position)
            .withValue(value)
            .build();
    return new ZeebeRecord(TOPIC, 0, 0L, record);
  }

  // ---- test doubles -------------------------------------------------------------------------

  /**
   * Captures every appended row as a {@code column index -> value} map — never engages
   * backpressure. Mirrors {@code LakeTranslatorObjectFabricTest}'s own test double (duplicated here
   * rather than shared, per this module's existing convention).
   */
  private static final class CapturingRowAppender implements RowAppender {
    private final List<Map<Integer, Object>> rows = new ArrayList<>();
    private Map<Integer, Object> current;

    @Override
    public boolean begin() {
      current = new HashMap<>();
      return true;
    }

    @Override
    public RowAppender putLong(final int column, final long value) {
      current.put(column, value);
      return this;
    }

    @Override
    public RowAppender putInt(final int column, final int value) {
      current.put(column, value);
      return this;
    }

    @Override
    public RowAppender putDict(final int column, final CharSequence value) {
      current.put(column, value.toString());
      return this;
    }

    @Override
    public RowAppender putBinary(
        final int column, final byte[] src, final int offset, final int len) {
      current.put(column, new String(src, offset, len, StandardCharsets.UTF_8));
      return this;
    }

    @Override
    public RowAppender putDouble(final int column, final double value) {
      current.put(column, value);
      return this;
    }

    @Override
    public RowAppender putNull(final int column) {
      current.put(column, null);
      return this;
    }

    @Override
    public void endRow() {
      rows.add(current);
      current = null;
    }
  }

  /**
   * Reports backpressure ({@code begin()} returns {@code false}) exactly {@code
   * beginFailuresRemaining} times before accepting every subsequent row, or (via {@link
   * #failOnNthBegin}) on one specific {@code begin()} call number instead. Mirrors {@code
   * LakeTranslatorObjectFabricTest}'s own test double.
   */
  private static final class FlakyRowAppender implements RowAppender {
    private int beginFailuresRemaining;
    private int rowsAppended;
    private int beginCallCount;
    private int failOnCallNumber = -1;

    FlakyRowAppender(final int beginFailuresRemaining) {
      this.beginFailuresRemaining = beginFailuresRemaining;
    }

    /** Fails ONLY the {@code n}-th (1-based) {@code begin()} call, across the whole lifetime. */
    void failOnNthBegin(final int n) {
      failOnCallNumber = n;
    }

    @Override
    public boolean begin() {
      beginCallCount++;
      if (failOnCallNumber == beginCallCount) {
        return false;
      }
      if (beginFailuresRemaining > 0) {
        beginFailuresRemaining--;
        return false;
      }
      return true;
    }

    @Override
    public RowAppender putLong(final int column, final long value) {
      return this;
    }

    @Override
    public RowAppender putInt(final int column, final int value) {
      return this;
    }

    @Override
    public RowAppender putDict(final int column, final CharSequence value) {
      return this;
    }

    @Override
    public RowAppender putBinary(
        final int column, final byte[] src, final int offset, final int len) {
      return this;
    }

    @Override
    public RowAppender putDouble(final int column, final double value) {
      return this;
    }

    @Override
    public RowAppender putNull(final int column) {
      return this;
    }

    @Override
    public void endRow() {
      rowsAppended++;
    }
  }

  /**
   * Captures every appended row per table instead of writing Parquet — mirrors MetricsRiderTest.
   */
  private static final class RecordingEncoderFactory implements BatchEncoder.Factory {

    private final Map<String, List<Map<String, Object>>> rowsByTable = new HashMap<>();

    List<Map<String, Object>> rows(final String table) {
      return rowsByTable.getOrDefault(table, List.of());
    }

    @Override
    public BatchEncoder newFile(final TableSchema schema, final long epochDay) {
      final List<Map<String, Object>> sink =
          rowsByTable.computeIfAbsent(schema.table(), t -> new ArrayList<>());
      return new BatchEncoder() {
        private long rowCount;

        @Override
        public void append(final SortedRun run, final int fromIndex, final int toIndex) {
          for (int i = fromIndex; i < toIndex; i++) {
            final Map<String, Object> row = new HashMap<>();
            for (int c = 0; c < schema.columns().size(); c++) {
              final TableSchema.Column column = schema.columns().get(c);
              if (run.isNullAt(c, i)) {
                row.put(column.name(), null);
              } else if (column.type() == ColumnType.STRING_DICT) {
                row.put(column.name(), run.stringAt(c, i));
              } else if (column.type() == ColumnType.INT) {
                row.put(column.name(), run.intAt(c, i));
              } else {
                row.put(column.name(), run.longAt(c, i));
              }
            }
            sink.add(row);
            rowCount++;
          }
        }

        @Override
        public DataFileResult finish() {
          return new DataFileResult(
              schema.table(),
              "mem://" + schema.table() + "/" + epochDay,
              rowCount,
              rowCount * 64,
              new Metrics(rowCount, null, null, null, null),
              epochDay);
        }

        @Override
        public void abort() {}
      };
    }
  }

  /**
   * Minimal in-memory {@link TranslatorState} (mirrors {@code LakeTranslatorObjectFabricTest}'s own
   * test double, duplicated here rather than shared).
   */
  private static final class InMemoryTranslatorState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();
    private final Map<Long, VariantAccumulator> variantAccumulators = new HashMap<>();
    private final Map<String, VariantName> variantNames = new HashMap<>();
    private final Map<String, FlowEndpoints> flowEndpointsByKey = new HashMap<>();
    private final Map<Long, ObjectSightingList> objectSightings = new HashMap<>();
    private final Map<String, ObjectLifecycle> objectLifecycle = new HashMap<>();

    @Override
    public void putFlowEndpoints(
        final long processDefinitionKey, final String flowId, final FlowEndpoints endpoints) {
      flowEndpointsByKey.put(processDefinitionKey + "#" + flowId, endpoints);
    }

    @Override
    public FlowEndpoints flowEndpoints(final long processDefinitionKey, final String flowId) {
      return flowEndpointsByKey.get(processDefinitionKey + "#" + flowId);
    }

    @Override
    public void putInstance(final long instanceKey, final OpenInstance instance) {
      instances.put(instanceKey, instance);
    }

    @Override
    public OpenInstance getInstance(final long instanceKey) {
      return instances.get(instanceKey);
    }

    @Override
    public void deleteInstance(final long instanceKey) {
      instances.remove(instanceKey);
    }

    @Override
    public void putElement(final long elementKey, final OpenElement element) {
      elements.put(elementKey, element);
    }

    @Override
    public OpenElement getElement(final long elementKey) {
      return elements.get(elementKey);
    }

    @Override
    public void deleteElement(final long elementKey) {
      elements.remove(elementKey);
    }

    @Override
    public void putVariable(final long instanceKey, final String name, final String valueJson) {
      variables.computeIfAbsent(instanceKey, k -> new HashMap<>()).put(name, valueJson);
    }

    @Override
    public Map<String, String> variablesOf(final long instanceKey) {
      return variables.getOrDefault(instanceKey, Map.of());
    }

    @Override
    public void deleteVariablesOf(final long instanceKey) {
      variables.remove(instanceKey);
    }

    @Override
    public void putVariantAccumulator(
        final long instanceKey, final VariantAccumulator accumulator) {
      variantAccumulators.put(instanceKey, accumulator);
    }

    @Override
    public VariantAccumulator getVariantAccumulator(final long instanceKey) {
      return variantAccumulators.get(instanceKey);
    }

    @Override
    public void deleteVariantAccumulator(final long instanceKey) {
      variantAccumulators.remove(instanceKey);
    }

    @Override
    public void putVariantName(final String bpmnProcessId, final int h32, final VariantName name) {
      variantNames.put(bpmnProcessId + '#' + h32, name);
    }

    @Override
    public VariantName getVariantName(final String bpmnProcessId, final int h32) {
      return variantNames.get(bpmnProcessId + '#' + h32);
    }

    @Override
    public void putObjectSightings(final long instanceKey, final ObjectSightingList sightings) {
      objectSightings.put(instanceKey, sightings);
    }

    @Override
    public ObjectSightingList getObjectSightings(final long instanceKey) {
      return objectSightings.get(instanceKey);
    }

    @Override
    public void deleteObjectSightings(final long instanceKey) {
      objectSightings.remove(instanceKey);
    }

    @Override
    public void putObjectLifecycle(
        final String objectType, final String objectId, final ObjectLifecycle lifecycle) {
      objectLifecycle.put(objectType + '#' + objectId, lifecycle);
    }

    @Override
    public ObjectLifecycle getObjectLifecycle(final String objectType, final String objectId) {
      return objectLifecycle.get(objectType + '#' + objectId);
    }

    @Override
    public int sweepObjectLifecycleTombstones(final long cutoffMs) {
      final int before = objectLifecycle.size();
      objectLifecycle
          .values()
          .removeIf(
              lifecycle ->
                  lifecycle.status() == LifecycleStatus.CLOSED_TOMBSTONE
                      && lifecycle.closedAtMs() < cutoffMs);
      return before - objectLifecycle.size();
    }

    @Override
    public void forEachOpenInstance(final BiConsumer<Long, OpenInstance> consumer) {
      instances.forEach(consumer);
    }

    @Override
    public void forEachOpenElement(final BiConsumer<Long, OpenElement> consumer) {
      elements.forEach(consumer);
    }

    @Override
    public void close() {}
  }
}

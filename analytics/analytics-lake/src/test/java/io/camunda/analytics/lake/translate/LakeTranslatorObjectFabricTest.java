/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.objects.CompiledObjectType;
import io.camunda.analytics.lake.objects.CompiledObjectTypes;
import io.camunda.analytics.lake.objects.ObjectTypes;
import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.translate.RawTableSchemas.ActivityColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.InstanceLinkColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.ObjectColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.ObjectRelationColumns;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LakeTranslator}'s OCPM object fabric capture (see its class javadoc's
 * "Object fabric capture" section): sightings from all three sources (incl. scope handling, the
 * root-scope-NULL rule, and the non-scalar-never-sights rule), seen-cache suppression,
 * call-activity instance links, object-relations derivation at completion (incl. the self-relation
 * skip and the per-instance cap/overflow), {@code activities}' {@code flow_scope_key} column, and
 * the backpressure-absorb judgment call shared by every new dictionary appender.
 */
class LakeTranslatorObjectFabricTest {

  private static final String TOPIC = "test-topic";
  private static final String PROCESS_ID = "object-fabric-test-process";
  private static final int VERSION = 1;
  private static final String TENANT_ID = "<default>";
  private static final long PROCESS_DEFINITION_KEY = 1L;
  private static final int ZEEBE_PARTITION = 1;

  private static final CompiledObjectType CUSTOMER_TYPE =
      ObjectTypes.declare("customer").identifiedBy(ObjectTypes.variable("customerId")).build();

  /**
   * Declares BOTH a variable identifier (exercised by the VARIABLE-sighting/relations tests below)
   * and a correlation-key identifier (exercised by the message-correlation-sighting tests) — legal
   * per {@link CompiledObjectTypes#of}'s own rules (only two DIFFERENT types clashing over the same
   * variable name, or two DIFFERENT types both claiming correlation-key identity, are rejected),
   * and lets one shared registry back every test in this class instead of one per sighting source.
   */
  private static final CompiledObjectType DISPUTE_TYPE =
      ObjectTypes.declare("dispute")
          .identifiedBy(ObjectTypes.variable("correlationKey"))
          .identifiedBy(ObjectTypes.correlationKey("correlationKey"))
          .build();

  private static final CompiledObjectTypes OBJECT_TYPES =
      CompiledObjectTypes.of(CUSTOMER_TYPE, DISPUTE_TYPE);

  // ---- sightings: VARIABLE source ------------------------------------------------------------

  @Test
  void shouldSightScalarStringVariableAtRootScopeAsNullScopeKey() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "\"abc-123\"", 1L, 101L, 2L));

    // then
    assertThat(objects.rows).hasSize(1);
    final Map<Integer, Object> row = objects.rows.get(0);
    assertThat(row.get(ObjectColumns.OBJECT_TYPE)).isEqualTo("customer");
    assertThat(row.get(ObjectColumns.OBJECT_ID)).isEqualTo("abc-123");
    assertThat(row.get(ObjectColumns.INSTANCE_KEY)).isEqualTo(1L);
    assertThat(row.get(ObjectColumns.PROCESS_ID)).isEqualTo(PROCESS_ID);
    assertThat(row.get(ObjectColumns.SCOPE_KEY)).isNull(); // root -- see class javadoc
    assertThat(row.get(ObjectColumns.QUALIFIER)).isEqualTo("VARIABLE");
  }

  @Test
  void shouldSightScalarNumberVariableUsingTheRawUnquotedToken() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "456", 1L, 101L, 2L));

    // then
    assertThat(objects.rows.get(0).get(ObjectColumns.OBJECT_ID)).isEqualTo("456");
  }

  @Test
  void shouldRecordNonRootScopeKeyVerbatim() {
    // given: the variable's scope is a subprocess (scopeKey != instanceKey)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 55L, 101L, 2L));

    // then
    assertThat(objects.rows.get(0).get(ObjectColumns.SCOPE_KEY)).isEqualTo(55L);
  }

  @Test
  void shouldNeverSightAnObjectValue() {
    assertNoSightingForValue("{\"a\":1}");
  }

  @Test
  void shouldNeverSightAnArrayValue() {
    assertNoSightingForValue("[1,2,3]");
  }

  @Test
  void shouldNeverSightABooleanValue() {
    assertNoSightingForValue("true");
  }

  @Test
  void shouldNeverSightANullValue() {
    assertNoSightingForValue("null");
  }

  @Test
  void shouldNeverSightABlankStringValue() {
    assertNoSightingForValue("\"\"");
  }

  private void assertNoSightingForValue(final String valueJson) {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", valueJson, 1L, 101L, 2L));

    // then
    assertThat(objects.rows).isEmpty();
  }

  @Test
  void shouldUnescapeJsonStringValueForTheObjectId() {
    // given: the JSON token 'a\"b' -- a literal quote inside the string, escaped
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "\"a\\\"b\"", 1L, 101L, 2L));

    // then
    assertThat(objects.rows.get(0).get(ObjectColumns.OBJECT_ID)).isEqualTo("a\"b");
  }

  @Test
  void shouldIgnoreAVariableNotMatchingAnyDeclaredIdentifier() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "somethingElse", "\"x\"", 1L, 101L, 2L));

    // then
    assertThat(objects.rows).isEmpty();
  }

  @Test
  void shouldNoOpSightingDetectionWhenObjectTypesUnwired() {
    // given: objectTypes == null disables sighting detection entirely
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, null);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 101L, 2L));

    // then
    assertThat(objects.rows).isEmpty();
  }

  @Test
  void shouldSuppressDuplicateSightingOfTheSameDistinctKey() {
    // given: two separate UPDATED events carrying the identical (type, id, instance, scope)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 101L, 2L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 102L, 3L));

    // then: only the first sighting produced a dictionary row
    assertThat(objects.rows).hasSize(1);
  }

  @Test
  void shouldSkipDictionaryRowButStillRecordSightingWhenObjectsAppenderUnwired() {
    // given: objectsAppender == null disables row emission, not detection/CF-7 bookkeeping
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 101L, 2L));

    // then: the sighting is still recorded in state for relations derivation
    final TranslatorState.ObjectSightingList sightings = state.getObjectSightings(1L);
    assertThat(sightings).isNotNull();
    assertThat(sightings.sightings()).hasSize(1);
  }

  @Test
  void shouldSkipDictionaryRowAndAllowLaterRetryWhenOwningInstanceIsMissing() {
    // given: a variable arrives for an instance whose OpenInstance state is unknown (e.g. state
    // loss) -- the row cannot carry process_id/version, so it is skipped and the seen-cache entry
    // is un-marked so a later sighting of the identical key gets another chance
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    // deliberately no activateRoot() call -- state.getInstance(1L) is null

    // when: first attempt, instance still missing
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 101L, 2L));
    assertThat(objects.rows).isEmpty();

    // and then: the instance becomes known and the identical sighting recurs
    state.putInstance(
        1L,
        new TranslatorState.OpenInstance(
            PROCESS_DEFINITION_KEY, PROCESS_ID, VERSION, TENANT_ID, 100L));
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 102L, 3L));

    // then: the retry succeeds
    assertThat(objects.rows).hasSize(1);
  }

  @Test
  void shouldAbsorbBackpressureOnTheObjectsAppenderAndRetryOnALaterSighting() {
    // given: begin() fails exactly once
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender objects = new FlakyRowAppender(1);
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when: the first attempt hits backpressure -- absorbed, never propagated
    final boolean firstAttempt =
        translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 101L, 2L));
    assertThat(firstAttempt).isTrue();
    assertThat(objects.rowsAppended).isZero();

    // and when: an identical later sighting retries (seen-cache entry was un-marked)
    translator.onRecord(variableRecord(1L, "customerId", "\"abc\"", 1L, 102L, 3L));

    // then
    assertThat(objects.rowsAppended).isEqualTo(1);
  }

  // ---- sightings: PROCESS_MESSAGE_SUBSCRIPTION CORRELATED ------------------------------------

  @Test
  void shouldSightCorrelatedProcessMessageSubscriptionAtRootScope() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(processMessageSubscriptionCorrelated(1L, "corr-key-1", 101L, 2L));

    // then
    assertThat(objects.rows).hasSize(1);
    final Map<Integer, Object> row = objects.rows.get(0);
    assertThat(row.get(ObjectColumns.OBJECT_TYPE)).isEqualTo("dispute");
    assertThat(row.get(ObjectColumns.OBJECT_ID)).isEqualTo("corr-key-1");
    assertThat(row.get(ObjectColumns.SCOPE_KEY)).isNull(); // documented v1 approximation
    assertThat(row.get(ObjectColumns.QUALIFIER)).isEqualTo("MESSAGE");
  }

  @Test
  void shouldIgnoreBlankCorrelationKeyOnProcessMessageSubscription() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(processMessageSubscriptionCorrelated(1L, "", 101L, 2L));

    // then
    assertThat(objects.rows).isEmpty();
  }

  @Test
  void shouldNoOpProcessMessageSubscriptionSightingWhenNoTypeDeclaresCorrelationKeyIdentity() {
    // given: a registry with no correlation-key declarer at all
    final CompiledObjectTypes noCorrelationTypes = CompiledObjectTypes.of(CUSTOMER_TYPE);
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, noCorrelationTypes);
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // when
    translator.onRecord(processMessageSubscriptionCorrelated(1L, "corr-key-1", 101L, 2L));

    // then
    assertThat(objects.rows).isEmpty();
  }

  // ---- sightings: MESSAGE_START_EVENT_SUBSCRIPTION CORRELATED --------------------------------

  @Test
  void shouldSightMessageStartCorrelationAtRootScopeExactly() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(2L, 100L, 1L));

    // when
    translator.onRecord(messageStartEventSubscriptionCorrelated(2L, "corr-key-2", 101L, 2L));

    // then
    assertThat(objects.rows).hasSize(1);
    final Map<Integer, Object> row = objects.rows.get(0);
    assertThat(row.get(ObjectColumns.QUALIFIER)).isEqualTo("MESSAGE_START");
    assertThat(row.get(ObjectColumns.SCOPE_KEY)).isNull();
  }

  @Test
  void shouldIgnoreMessageStartSubscriptionWithNoProcessInstanceKeySet() {
    // given: processInstanceKey defaults to 0 when not explicitly set on the builder (not
    // actually correlated yet -- defensive path)
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender objects = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, objects, null, null, OBJECT_TYPES);

    // when
    final MessageStartEventSubscriptionRecordValue value =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withStartEventId("start")
            .withMessageName("msg")
            .withCorrelationKey("corr")
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .build();
    translator.onRecord(
        wrapRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionIntent.CORRELATED,
            0L,
            100L,
            1L,
            value));

    // then
    assertThat(objects.rows).isEmpty();
  }

  // ---- instance links (call-activity) ---------------------------------------------------------

  @Test
  void shouldEmitInstanceLinkForACallActivityChild() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender links = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, links, null, OBJECT_TYPES);

    // when: child instance 2's root activation names parent instance 1 via element instance 99
    translator.onRecord(activateChildRoot(2L, 1L, 99L, 100L, 1L));

    // then
    assertThat(links.rows).hasSize(1);
    final Map<Integer, Object> row = links.rows.get(0);
    assertThat(row.get(InstanceLinkColumns.PARENT_INSTANCE_KEY)).isEqualTo(1L);
    assertThat(row.get(InstanceLinkColumns.CHILD_INSTANCE_KEY)).isEqualTo(2L);
    assertThat(row.get(InstanceLinkColumns.LINK_TYPE)).isEqualTo("CALL_ACTIVITY");
    assertThat(row.get(InstanceLinkColumns.VIA_ELEMENT_INSTANCE_KEY)).isEqualTo(99L);
  }

  @Test
  void shouldEmitNullViaElementInstanceKeyWhenNotSet() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender links = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, links, null, OBJECT_TYPES);

    // when
    translator.onRecord(activateChildRoot(2L, 1L, 0L, 100L, 1L));

    // then
    assertThat(links.rows.get(0).get(InstanceLinkColumns.VIA_ELEMENT_INSTANCE_KEY)).isNull();
  }

  @Test
  void shouldNotEmitALinkForATopLevelInstance() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender links = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, links, null, OBJECT_TYPES);

    // when: a plain root activation, no call-activity parent
    translator.onRecord(activateRoot(1L, 100L, 1L));

    // then
    assertThat(links.rows).isEmpty();
  }

  @Test
  void shouldSuppressADuplicateLinkForTheSameChildInstance() {
    // given: the child's root activation is (re-)folded twice -- the seen-cache dedups by child key
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender links = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, links, null, OBJECT_TYPES);
    translator.onRecord(activateChildRoot(2L, 1L, 99L, 100L, 1L));

    // when
    translator.onRecord(activateChildRoot(2L, 1L, 99L, 100L, 2L));

    // then
    assertThat(links.rows).hasSize(1);
  }

  @Test
  void shouldAbsorbBackpressureOnTheInstanceLinksAppender() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender links = new FlakyRowAppender(1);
    final LakeTranslator translator = newTranslator(state, null, links, null, OBJECT_TYPES);

    // when: first attempt hits backpressure
    final boolean firstAttempt = translator.onRecord(activateChildRoot(2L, 1L, 99L, 100L, 1L));
    assertThat(firstAttempt).isTrue();
    assertThat(links.rowsAppended).isZero();

    // and when: a later attempt (e.g. a redelivered activation) retries
    translator.onRecord(activateChildRoot(2L, 1L, 99L, 100L, 2L));

    // then
    assertThat(links.rowsAppended).isEqualTo(1);
  }

  // ---- object relations at completion ----------------------------------------------------------

  @Test
  void shouldEmitARelationForARootAndANonRootSightingOfDifferentObjects() {
    // given: "customer" sighted at root, "dispute" sighted at a non-root scope
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender relations = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, null, relations, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 101L, 2L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 55L, 102L, 3L));

    // when
    translator.onRecord(completeRoot(1L, 200L, 4L));

    // then
    assertThat(relations.rows).hasSize(1);
    final Map<Integer, Object> row = relations.rows.get(0);
    assertThat(row.get(ObjectRelationColumns.PARENT_TYPE)).isEqualTo("customer");
    assertThat(row.get(ObjectRelationColumns.PARENT_ID)).isEqualTo("cust-1");
    assertThat(row.get(ObjectRelationColumns.CHILD_TYPE)).isEqualTo("dispute");
    assertThat(row.get(ObjectRelationColumns.CHILD_ID)).isEqualTo("disp-1");
  }

  @Test
  void shouldSkipASelfRelationWhenTheSameObjectIsSightedAtBothScopes() {
    // given: the identical (type, id) sighted once at root and once at a non-root scope
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender relations = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, null, relations, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 101L, 2L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 55L, 102L, 3L));

    // when
    translator.onRecord(completeRoot(1L, 200L, 4L));

    // then: a self-relation is never emitted
    assertThat(relations.rows).isEmpty();
  }

  @Test
  void shouldNotEmitARelationWhenOnlyRootSightingsExist() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender relations = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, null, relations, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 101L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 200L, 4L));

    // then
    assertThat(relations.rows).isEmpty();
  }

  @Test
  void shouldSuppressADuplicateRelationAcrossDifferentInstances() {
    // given: two different instances both produce the exact same (parent, child) edge
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender relations = new CapturingRowAppender();
    final LakeTranslator translator = newTranslator(state, null, null, relations, OBJECT_TYPES);

    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 101L, 2L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 55L, 102L, 3L));
    translator.onRecord(completeRoot(1L, 200L, 4L));

    translator.onRecord(activateRoot(2L, 100L, 5L));
    translator.onRecord(variableRecord(2L, "customerId", "\"cust-1\"", 2L, 101L, 6L));
    translator.onRecord(variableRecord(2L, "correlationKey", "\"disp-1\"", 66L, 102L, 7L));

    // when
    translator.onRecord(completeRoot(2L, 200L, 8L));

    // then: only the first instance's completion produced a relation row
    assertThat(relations.rows).hasSize(1);
  }

  @Test
  void shouldAbsorbBackpressureOnTheObjectRelationsAppender() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final FlakyRowAppender relations = new FlakyRowAppender(1);
    final LakeTranslator translator = newTranslator(state, null, null, relations, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 101L, 2L));
    translator.onRecord(variableRecord(1L, "correlationKey", "\"disp-1\"", 55L, 102L, 3L));
    translator.onRecord(completeRoot(1L, 200L, 4L));
    assertThat(relations.rowsAppended).isZero(); // first attempt absorbed

    // when: a second instance reproduces the identical edge -- retries the un-marked key
    translator.onRecord(activateRoot(2L, 100L, 5L));
    translator.onRecord(variableRecord(2L, "customerId", "\"cust-1\"", 2L, 101L, 6L));
    translator.onRecord(variableRecord(2L, "correlationKey", "\"disp-1\"", 66L, 102L, 7L));
    translator.onRecord(completeRoot(2L, 200L, 8L));

    // then
    assertThat(relations.rowsAppended).isEqualTo(1);
  }

  @Test
  void shouldCapTheSightingListAndSetTheOverflowFlag() {
    // given: an instance that sights more distinct root-scope objects than the documented cap
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    long position = 2L;
    // MAX_OBJECT_SIGHTINGS_PER_INSTANCE is 64 -- sight 70 distinct non-root "customer" ids so the
    // cap is exceeded without ever hitting the seen-cache for a repeat.
    for (int i = 0; i < 70; i++) {
      translator.onRecord(
          variableRecord(1L, "customerId", "\"cust-" + i + "\"", 1000L + i, 101L, position++));
    }

    // when
    final TranslatorState.ObjectSightingList sightings = state.getObjectSightings(1L);

    // then
    assertThat(sightings).isNotNull();
    assertThat(sightings.overflowed()).isTrue();
    assertThat(sightings.sightings()).hasSize(64);
  }

  @Test
  void shouldEvictTheSightingListOnCompletionEvenWithNoRelationsAppenderWired() {
    // given
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final LakeTranslator translator = newTranslator(state, null, null, null, OBJECT_TYPES);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(variableRecord(1L, "customerId", "\"cust-1\"", 1L, 101L, 2L));

    // when
    translator.onRecord(completeRoot(1L, 200L, 3L));

    // then
    assertThat(state.getObjectSightings(1L)).isNull();
  }

  // ---- schema v4: activities.flow_scope_key ----------------------------------------------------

  @Test
  void shouldEmitNullFlowScopeKeyForATopLevelElement() {
    // given: the element's own flow scope key equals the owning instance key
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender activities = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, new CapturingRowAppender(), activities);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", 1L, 101L, 2L));

    // when
    translator.onRecord(completeElement(11L, 1L, 150L, 3L));

    // then
    assertThat(activities.rows.get(0).get(ActivityColumns.FLOW_SCOPE_KEY)).isNull();
  }

  @Test
  void shouldEmitTheFlowScopeKeyForANestedElement() {
    // given: the element's flow scope is a subprocess element instance, not the root
    final InMemoryTranslatorState state = new InMemoryTranslatorState();
    final CapturingRowAppender activities = new CapturingRowAppender();
    final LakeTranslator translator =
        new LakeTranslator(state, new CapturingRowAppender(), activities);
    translator.onRecord(activateRoot(1L, 100L, 1L));
    translator.onRecord(activateElement(1L, 11L, "task-a", 77L, 101L, 2L));

    // when
    translator.onRecord(completeElement(11L, 77L, 150L, 3L));

    // then
    assertThat(activities.rows.get(0).get(ActivityColumns.FLOW_SCOPE_KEY)).isEqualTo(77L);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private static LakeTranslator newTranslator(
      final TranslatorState state,
      final RowAppender objectsAppender,
      final RowAppender instanceLinksAppender,
      final RowAppender objectRelationsAppender,
      final CompiledObjectTypes objectTypes) {
    return new LakeTranslator(
        state,
        new CapturingRowAppender(),
        new CapturingRowAppender(),
        null,
        null,
        null,
        null,
        objectsAppender,
        instanceLinksAppender,
        objectRelationsAppender,
        objectTypes);
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

  private static ZeebeRecord activateChildRoot(
      final long instanceKey,
      final long parentProcessInstanceKey,
      final long parentElementInstanceKey,
      final long timestamp,
      final long position) {
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
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withParentElementInstanceKey(parentElementInstanceKey)
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
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        instanceKey,
        timestamp,
        position,
        value);
  }

  private static ZeebeRecord activateElement(
      final long instanceKey,
      final long elementInstanceKey,
      final String elementId,
      final long flowScopeKey,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(instanceKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId(elementId)
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .withFlowScopeKey(flowScopeKey)
            .build();
    return wrapRecord(
        ValueType.PROCESS_INSTANCE,
        ProcessInstanceIntent.ELEMENT_ACTIVATED,
        elementInstanceKey,
        timestamp,
        position,
        value);
  }

  private static ZeebeRecord completeElement(
      final long elementInstanceKey,
      final long flowScopeKey,
      final long timestamp,
      final long position) {
    final ProcessInstanceRecordValue value =
        ImmutableProcessInstanceRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withVersion(VERSION)
            .withProcessInstanceKey(1L)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withTenantId(TENANT_ID)
            .withElementId("task-a")
            .withBpmnElementType(BpmnElementType.SERVICE_TASK)
            .withFlowScopeKey(flowScopeKey)
            .build();
    return wrapRecord(
        ValueType.PROCESS_INSTANCE,
        ProcessInstanceIntent.ELEMENT_COMPLETED,
        elementInstanceKey,
        timestamp,
        position,
        value);
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

  private static ZeebeRecord processMessageSubscriptionCorrelated(
      final long processInstanceKey,
      final String correlationKey,
      final long timestamp,
      final long position) {
    final ProcessMessageSubscriptionRecordValue value =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withElementId("catch")
            .withMessageName("msg")
            .withCorrelationKey(correlationKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(999L)
            .withTenantId(TENANT_ID)
            .build();
    return wrapRecord(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
        ProcessMessageSubscriptionIntent.CORRELATED,
        processInstanceKey,
        timestamp,
        position,
        value);
  }

  private static ZeebeRecord messageStartEventSubscriptionCorrelated(
      final long processInstanceKey,
      final String correlationKey,
      final long timestamp,
      final long position) {
    final MessageStartEventSubscriptionRecordValue value =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withBpmnProcessId(PROCESS_ID)
            .withStartEventId("start")
            .withMessageName("msg")
            .withCorrelationKey(correlationKey)
            .withProcessDefinitionKey(PROCESS_DEFINITION_KEY)
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(TENANT_ID)
            .build();
    return wrapRecord(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
        MessageStartEventSubscriptionIntent.CORRELATED,
        processInstanceKey,
        timestamp,
        position,
        value);
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
   * backpressure. Mirrors {@code LakeTranslatorVariantTest}'s own test double.
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
   * beginFailuresRemaining} times before accepting every subsequent row. Mirrors {@code
   * LakeTranslatorTest}'s own test double.
   */
  private static final class FlakyRowAppender implements RowAppender {
    private int beginFailuresRemaining;
    private int rowsAppended;

    FlakyRowAppender(final int beginFailuresRemaining) {
      this.beginFailuresRemaining = beginFailuresRemaining;
    }

    @Override
    public boolean begin() {
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
   * Minimal in-memory {@link TranslatorState} (mirrors {@code LakeTranslatorVariantTest}'s own test
   * double, duplicated here rather than shared), with real in-memory object-sighting storage so
   * this test can assert on it directly.
   */
  private static final class InMemoryTranslatorState implements TranslatorState {
    private final Map<Long, OpenInstance> instances = new HashMap<>();
    private final Map<Long, OpenElement> elements = new HashMap<>();
    private final Map<Long, Map<String, String>> variables = new HashMap<>();
    private final Map<Long, VariantAccumulator> variantAccumulators = new HashMap<>();
    private final Map<String, VariantName> variantNames = new HashMap<>();
    private final Map<String, FlowEndpoints> flowEndpointsByKey = new HashMap<>();
    private final Map<Long, ObjectSightingList> objectSightings = new HashMap<>();

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

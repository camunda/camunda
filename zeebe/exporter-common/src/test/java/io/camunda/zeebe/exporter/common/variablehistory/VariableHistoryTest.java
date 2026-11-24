/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.variablehistory;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration.ActorAuditLogConfiguration;
import io.camunda.zeebe.exporter.common.variablehistory.VariableHistory.Actor;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.Agent;
import io.camunda.zeebe.protocol.record.ImmutableAgent;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableOperationType;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class VariableHistoryTest {

  private static final Map<String, Object> CLIENT_IDENTITY =
      Map.of(Authorization.AUTHORIZED_CLIENT_ID, "billing-client");
  private static final Map<String, Object> USER_IDENTITY =
      Map.of(Authorization.AUTHORIZED_USERNAME, "demo");
  private static final Map<String, Object> ANONYMOUS_IDENTITY =
      Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true);

  private static final Set<VariableOperationType> ENGINE_TYPES =
      EnumSet.of(
          VariableOperationType.INPUT_MAPPING,
          VariableOperationType.OUTPUT_MAPPING,
          VariableOperationType.SCRIPT_RESULT,
          VariableOperationType.DECISION_RESULT,
          VariableOperationType.CALL_ACTIVITY_INPUT,
          VariableOperationType.CALL_ACTIVITY_OUTPUT,
          VariableOperationType.MULTI_INSTANCE_INPUT_ELEMENT,
          VariableOperationType.MULTI_INSTANCE_LOOP_COUNTER,
          VariableOperationType.MULTI_INSTANCE_OUTPUT_ELEMENT,
          VariableOperationType.MULTI_INSTANCE_OUTPUT_COLLECTION,
          VariableOperationType.AD_HOC_SUB_PROCESS_METADATA,
          VariableOperationType.AD_HOC_SUB_PROCESS_OUTPUT_ELEMENT,
          VariableOperationType.AD_HOC_SUB_PROCESS_OUTPUT_COLLECTION);
  private static final Set<VariableOperationType> UNTAGGED_TYPES =
      EnumSet.of(VariableOperationType.UNKNOWN, VariableOperationType.MESSAGE_CORRELATION);

  private final ProtocolFactory factory = new ProtocolFactory();

  /** Every type that is neither an engine nor an untagged type, so a new type lands here. */
  private static Stream<VariableOperationType> requesterTypes() {
    final var types = EnumSet.allOf(VariableOperationType.class);
    types.removeAll(ENGINE_TYPES);
    types.removeAll(UNTAGGED_TYPES);
    return types.stream();
  }

  private static AuditLogConfiguration onlyUsers() {
    return new AuditLogConfiguration();
  }

  private static AuditLogConfiguration everything() {
    final var all = ActorAuditLogConfiguration.logAll();
    return new AuditLogConfiguration().setUser(all).setClient(all).setUnknown(all).setEngine(all);
  }

  private static ActorAuditLogConfiguration selecting(
      final AuditLogOperationCategory... categories) {
    return ActorAuditLogConfiguration.logNone().setCategories(Set.of(categories));
  }

  private static Agent agent(final String elementId) {
    return ImmutableAgent.builder().withElementId(elementId).build();
  }

  private Record<VariableRecordValue> variable(
      final VariableIntent intent,
      final VariableOperationType type,
      final Map<String, Object> authorizations,
      final @Nullable Agent agent) {
    return variable(intent, new VariableSourceRecord().setType(type), authorizations, agent);
  }

  private Record<VariableRecordValue> variable(
      final VariableIntent intent,
      final VariableSourceRecord source,
      final Map<String, Object> authorizations,
      final @Nullable Agent agent) {
    final VariableRecordValue value =
        ImmutableVariableRecordValue.builder()
            .from(factory.generateObject(VariableRecordValue.class))
            .withSource(source)
            .build();
    // the factory seeds a random agent and the record builder rejects a null one, so build it here
    final var builder =
        ImmutableRecord.<VariableRecordValue>builder()
            .withValueType(ValueType.VARIABLE)
            .withIntent(intent)
            .withValue(value)
            .withAuthorizations(authorizations);
    if (agent != null) {
      builder.withAgent(agent);
    }
    return builder.build();
  }

  @Nested
  class ActorOf {

    static Stream<VariableOperationType> engineTypes() {
      return ENGINE_TYPES.stream();
    }

    static Stream<VariableOperationType> untaggedTypes() {
      return UNTAGGED_TYPES.stream();
    }

    static Stream<VariableOperationType> requesterTypes() {
      return VariableHistoryTest.requesterTypes();
    }

    @ParameterizedTest
    @MethodSource("engineTypes")
    void shouldAttributeEngineTypesToTheEngine(final VariableOperationType type) {
      // given a record that names a client and an agent, as for the follow-up of an agent's job
      final var record =
          variable(VariableIntent.UPDATED, type, CLIENT_IDENTITY, agent("riskAgent"));

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.ENGINE, null, null));
    }

    @ParameterizedTest
    @MethodSource("requesterTypes")
    void shouldAttributeRequesterTypesToTheClient(final VariableOperationType type) {
      // given
      final var record = variable(VariableIntent.CREATED, type, CLIENT_IDENTITY, null);

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.CLIENT, "billing-client", null));
    }

    @Test
    void shouldAttributeARequesterTypeToTheUser() {
      // given
      final var record =
          variable(VariableIntent.CREATED, VariableOperationType.API, USER_IDENTITY, null);

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.USER, "demo", null));
    }

    @ParameterizedTest
    @MethodSource("untaggedTypes")
    void shouldAttributeUntaggedTypesToUnknownEvenWhenTheRecordNamesAClient(
        final VariableOperationType type) {
      // given a buffered message can be correlated under the command of another client
      final var record =
          variable(VariableIntent.CREATED, type, CLIENT_IDENTITY, agent("riskAgent"));

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.UNKNOWN, null, null));
    }

    @Test
    void shouldAttributeAnAgentChangeToItsClientAndTheAgentElementId() {
      // given
      final var record =
          variable(
              VariableIntent.UPDATED,
              VariableOperationType.JOB_COMPLETION,
              Map.of(Authorization.AUTHORIZED_CLIENT_ID, "connectors"),
              agent("riskAgent"));

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.CLIENT, "connectors", "riskAgent"));
    }

    @Test
    void shouldKeepTheAgentElementIdWhenAUserCompletedTheAgentsJob() {
      // given
      final var record =
          variable(
              VariableIntent.UPDATED,
              VariableOperationType.JOB_COMPLETION,
              USER_IDENTITY,
              agent("riskAgent"));

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.USER, "demo", "riskAgent"));
    }

    @Test
    void shouldNotSetTheAgentElementIdForAnAnonymousRequester() {
      // given
      final var record =
          variable(
              VariableIntent.UPDATED,
              VariableOperationType.JOB_COMPLETION,
              ANONYMOUS_IDENTITY,
              agent("riskAgent"));

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.ANONYMOUS, null, null));
    }

    @Test
    void shouldAttributeAnEngineTypeToTheEngineWhenTheRequestWasAnonymous() {
      // given authentication is off, so the identity on the record is anonymous
      final var record =
          variable(
              VariableIntent.UPDATED,
              VariableOperationType.OUTPUT_MAPPING,
              ANONYMOUS_IDENTITY,
              null);

      // when
      final var actor = VariableHistory.actorOf(record);

      // then the type decides first, as for the untagged types
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.ENGINE, null, null));
    }

    @Test
    void shouldAttributeARecordWithoutIdentityToUnknown() {
      // given
      final var record =
          variable(VariableIntent.CREATED, VariableOperationType.API, Map.of(), agent("riskAgent"));

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.UNKNOWN, null, null));
    }

    @Test
    void shouldAttributeAnEventWrittenBeforeTheUpgradeToUnknown() {
      // given the source of such an event has the defaults: UNKNOWN, no element key, no element id
      final var record =
          variable(VariableIntent.CREATED, new VariableSourceRecord(), USER_IDENTITY, null);

      // when
      final var actor = VariableHistory.actorOf(record);

      // then
      assertThat(actor).isEqualTo(new Actor(AuditLogActorType.UNKNOWN, null, null));
    }
  }

  @Nested
  class IsRecorded {

    static Stream<VariableOperationType> requesterTypes() {
      return VariableHistoryTest.requesterTypes();
    }

    static Stream<VariableOperationType> untaggedTypes() {
      return UNTAGGED_TYPES.stream();
    }

    @ParameterizedTest
    @EnumSource(
        value = VariableIntent.class,
        names = {"CREATED", "UPDATED"})
    void shouldRecordCreatedAndUpdatedEvents(final VariableIntent intent) {
      // given
      final var record = variable(intent, VariableOperationType.API, USER_IDENTITY, null);

      // when
      final var recorded = VariableHistory.isRecorded(record, onlyUsers());

      // then
      assertThat(recorded).isTrue();
    }

    @Test
    void shouldNeverRecordMigratedEvents() {
      // given a migration rewrites the variable with the same value
      final var record =
          variable(VariableIntent.MIGRATED, VariableOperationType.API, USER_IDENTITY, null);

      // when
      final var recorded = VariableHistory.isRecorded(record, everything());

      // then
      assertThat(recorded).isFalse();
    }

    @ParameterizedTest
    @MethodSource("requesterTypes")
    void shouldNeverRecordAnonymousRequests(final VariableOperationType type) {
      // given
      final var record = variable(VariableIntent.CREATED, type, ANONYMOUS_IDENTITY, null);

      // when
      final var recorded = VariableHistory.isRecorded(record, everything());

      // then
      assertThat(recorded).isFalse();
    }

    @ParameterizedTest
    @MethodSource("untaggedTypes")
    void shouldRecordAnUntaggedChangeOfAnAnonymousRequestOnlyWhenTheUnknownSectionSelectsIt(
        final VariableOperationType type) {
      // given the type decides first, so the actor is unknown and not anonymous
      final var record = variable(VariableIntent.CREATED, type, ANONYMOUS_IDENTITY, null);

      // when
      final var recordedWithoutUnknown = VariableHistory.isRecorded(record, onlyUsers());
      final var recordedWithUnknown =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setUnknown(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));

      // then
      assertThat(recordedWithoutUnknown).isFalse();
      assertThat(recordedWithUnknown).isTrue();
    }

    @ParameterizedTest
    @MethodSource("untaggedTypes")
    void shouldRecordAnUntaggedChangeOfAClientOnlyWhenTheUnknownSectionSelectsIt(
        final VariableOperationType type) {
      // given the record names a client, but its identity cannot be trusted for this type
      final var record = variable(VariableIntent.CREATED, type, CLIENT_IDENTITY, null);

      // when
      final var recordedWithClientSection =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setClient(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));
      final var recordedWithUnknownSection =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setUnknown(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));

      // then
      assertThat(recordedWithClientSection).isFalse();
      assertThat(recordedWithUnknownSection).isTrue();
    }

    @Test
    void shouldRecordAnEngineChangeOfAnAnonymousRequestOnlyWhenTheEngineSectionSelectsIt() {
      // given authentication is off, so the identity on the record is anonymous
      final var record =
          variable(
              VariableIntent.UPDATED,
              VariableOperationType.OUTPUT_MAPPING,
              ANONYMOUS_IDENTITY,
              null);

      // when
      final var recordedWithoutEngine = VariableHistory.isRecorded(record, onlyUsers());
      final var recordedWithEngine =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setEngine(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));

      // then
      assertThat(recordedWithoutEngine).isFalse();
      assertThat(recordedWithEngine).isTrue();
    }

    @Test
    void shouldRecordAnEngineChangeOnlyWhenTheEngineSectionSelectsIt() {
      // given the user section selects everything, the identity on the record is a user
      final var record =
          variable(
              VariableIntent.UPDATED, VariableOperationType.OUTPUT_MAPPING, USER_IDENTITY, null);

      // when
      final var recordedWithoutEngine = VariableHistory.isRecorded(record, onlyUsers());
      final var recordedWithEngine =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setEngine(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));

      // then
      assertThat(recordedWithoutEngine).isFalse();
      assertThat(recordedWithEngine).isTrue();
    }

    @Test
    void shouldRecordARequesterChangeOnlyWhenTheSectionOfItsActorSelectsIt() {
      // given
      final var record =
          variable(
              VariableIntent.UPDATED, VariableOperationType.JOB_COMPLETION, CLIENT_IDENTITY, null);

      // when
      final var recordedWithoutClient = VariableHistory.isRecorded(record, onlyUsers());
      final var recordedWithClient =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setClient(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));

      // then
      assertThat(recordedWithoutClient).isFalse();
      assertThat(recordedWithClient).isTrue();
    }

    @Test
    void shouldRecordAnEventWrittenBeforeTheUpgradeOnlyWhenTheUnknownSectionSelectsIt() {
      // given
      final var record =
          variable(VariableIntent.UPDATED, new VariableSourceRecord(), USER_IDENTITY, null);

      // when
      final var recordedWithoutUnknown = VariableHistory.isRecorded(record, onlyUsers());
      final var recordedWithUnknown =
          VariableHistory.isRecorded(
              record,
              new AuditLogConfiguration()
                  .setUnknown(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)));

      // then
      assertThat(recordedWithoutUnknown).isFalse();
      assertThat(recordedWithUnknown).isTrue();
    }

    @Test
    void shouldNotRecordWhenTheSectionExcludesVariables() {
      // given
      final var record =
          variable(VariableIntent.CREATED, VariableOperationType.API, USER_IDENTITY, null);
      final var configuration =
          new AuditLogConfiguration()
              .setUser(
                  selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES)
                      .setExcludes(Set.of(AuditLogEntityType.VARIABLE)));

      // when
      final var recorded = VariableHistory.isRecorded(record, configuration);

      // then
      assertThat(recorded).isFalse();
    }

    @Test
    void shouldNotRecordWhenTheAuditLogIsDisabled() {
      // given
      final var record =
          variable(VariableIntent.CREATED, VariableOperationType.API, USER_IDENTITY, null);
      final var configuration = everything();
      configuration.setEnabled(false);

      // when
      final var recorded = VariableHistory.isRecorded(record, configuration);

      // then
      assertThat(recorded).isFalse();
    }

    @Test
    void shouldRecordOnlyTheEngineChangeWhenTheUserAndEngineSectionsAreSelected() {
      // given the user and the engine section select DEPLOYED_RESOURCES, client and unknown are
      // empty
      final var configuration =
          new AuditLogConfiguration()
              .setUser(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES))
              .setEngine(selecting(AuditLogOperationCategory.DEPLOYED_RESOURCES));
      // billing-client completes a job with x, and an output mapping copies it into total
      final var x =
          variable(
              VariableIntent.CREATED, VariableOperationType.JOB_COMPLETION, CLIENT_IDENTITY, null);
      final var total =
          variable(
              VariableIntent.CREATED, VariableOperationType.OUTPUT_MAPPING, CLIENT_IDENTITY, null);
      // a buffered message that was correlated under the command of billing-client sets paid
      final var paid =
          variable(
              VariableIntent.CREATED,
              VariableOperationType.MESSAGE_CORRELATION,
              CLIENT_IDENTITY,
              null);

      // when
      final var xRecorded = VariableHistory.isRecorded(x, configuration);
      final var totalRecorded = VariableHistory.isRecorded(total, configuration);
      final var paidRecorded = VariableHistory.isRecorded(paid, configuration);

      // then
      assertThat(xRecorded).as("x, a client change").isFalse();
      assertThat(totalRecorded).as("total, an engine change").isTrue();
      assertThat(paidRecorded).as("paid, an untagged change").isFalse();
    }
  }
}

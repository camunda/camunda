/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.fanout.protocol.model.PolicyAuthorization;
import io.camunda.fanout.protocol.model.PolicyAuthorizationKey;
import io.camunda.fanout.protocol.model.PolicyChangeSetKind;
import io.camunda.fanout.protocol.model.PolicyChangeSetV1;
import io.camunda.fanout.protocol.model.PolicyDeletions;
import io.camunda.fanout.protocol.model.PolicyGroup;
import io.camunda.fanout.protocol.model.PolicyMappingRule;
import io.camunda.fanout.protocol.model.PolicyMembership;
import io.camunda.fanout.protocol.model.PolicyRole;
import io.camunda.fanout.protocol.model.PolicyTenant;
import io.camunda.fanout.protocol.model.PolicyUpserts;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.junit.Rule;
import org.junit.Test;

/**
 * Guards the assumption behind {@link PolicyChangeSetSplitter#DEFAULT_MAX_ENTRIES_PER_CHUNK}: the
 * identity command that applying an entry writes takes at most {@link #ASSUMED_OVERHEAD} bytes more
 * in the batch than the entry takes in its chunk. If record metadata or an identity record grows,
 * this test fails before a chunk of the maximum size no longer fits into the batch that applies it.
 *
 * <p>It applies every kind of entry, as created, updated and deleted, and compares the size of each
 * identity command in the log, as the batch counts it, with the size of its entry.
 */
public final class PolicyChangeSetCommandOverheadTest {

  /** Measured at about 140 to 200 bytes; the limit of the splitter assumes up to this. */
  private static final int ASSUMED_OVERHEAD = 256;

  /** What the batch counts per record besides metadata and value: key, source index, framing. */
  private static final int RECORD_FRAMING = Long.BYTES + Integer.BYTES + 12 + 8;

  private static final int ENTRIES_PER_KIND = 5;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String policyId = "policy-" + UUID.randomUUID();
  private final Map<String, Integer> smallestEntrySizes = new HashMap<>();

  @Test
  public void shouldWriteNoCommandMuchLargerThanItsEntry() {
    // given entries of every kind, created, then updated, then deleted
    final var upserts = upserts();
    apply(1, upserts, noDeletions());
    apply(2, upserts, noDeletions());
    apply(3, noUpserts(), deletions());

    // when
    final var overheads = new HashMap<String, Integer>();
    engine
        .events()
        .forEach(
            event -> {
              final var metadata = new RecordMetadata();
              event.readMetadata(metadata);
              if (metadata.getRecordType() == RecordType.COMMAND
                  && sectionOf(metadata.getValueType(), metadata.getIntent()) != null) {
                final var section = sectionOf(metadata.getValueType(), metadata.getIntent());
                final int overhead = sizeInBatch(event) - smallestEntrySizes.get(section);
                overheads.merge(
                    metadata.getValueType() + "." + metadata.getIntent(), overhead, Math::max);
              }
            });

    // then
    assertThat(overheads.keySet())
        .describedAs("the commands of every kind of entry")
        .containsAll(
            Set.of(
                "TENANT.CREATE",
                "TENANT.UPDATE",
                "TENANT.DELETE",
                "ROLE.DELETE",
                "GROUP.DELETE",
                "MAPPING_RULE.DELETE",
                "GROUP.ADD_ENTITY",
                "GROUP.REMOVE_ENTITY",
                "AUTHORIZATION.CREATE",
                "AUTHORIZATION.UPDATE",
                "AUTHORIZATION.DELETE"));
    assertThat(overheads)
        .allSatisfy(
            (command, overhead) ->
                assertThat(overhead)
                    .describedAs("bytes that %s takes more than its entry", command)
                    .isLessThanOrEqualTo(ASSUMED_OVERHEAD));
  }

  /** The section of the entries that the command applies, or {@code null} if no entry does. */
  private static String sectionOf(final ValueType valueType, final Intent intent) {
    if (valueType == ValueType.AUTHORIZATION) {
      return intent == AuthorizationIntent.DELETE ? "authorizationDeletes" : "authorizations";
    }
    if (!Set.of(ValueType.TENANT, ValueType.ROLE, ValueType.GROUP, ValueType.MAPPING_RULE)
        .contains(valueType)) {
      return null;
    }
    return switch (intent.name()) {
      case "ADD_ENTITY" -> "memberships";
      case "REMOVE_ENTITY" -> "membershipDeletes";
      case "DELETE" -> "deletes";
      case "CREATE", "UPDATE" -> "upserts of " + valueType;
      default -> null;
    };
  }

  private static int sizeInBatch(final LoggedEvent event) {
    return RECORD_FRAMING + event.getMetadataLength() + event.getValueLength();
  }

  private void apply(
      final long version, final PolicyUpserts upserts, final PolicyDeletions deletions) {
    final var changeSet =
        new PolicyChangeSetV1(
            policyId,
            "Policy",
            null,
            version - 1,
            version,
            PolicyChangeSetKind.DELTA,
            upserts,
            deletions);
    final var updateId = "update-" + UUID.randomUUID();
    recordSmallestEntrySizes(PolicyChangeSetMapper.map(changeSet, version, updateId).get());
    new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE)
        .split(changeSet, version, updateId)
        .get()
        .forEach(
            chunk ->
                engine.writeRecords(
                    RecordToWrite.commandWithoutClaims()
                        .policyUpdate(PolicyUpdateIntent.APPEND_CHUNK, chunk)));
    RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_COMMITTED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .await();
  }

  private void recordSmallestEntrySizes(final PolicyUpdateRecord update) {
    record("upserts of TENANT", update.getTenants(), PolicyUpdateRecord::setTenants, update);
    record("upserts of ROLE", update.getRoles(), PolicyUpdateRecord::setRoles, update);
    record("upserts of GROUP", update.getGroups(), PolicyUpdateRecord::setGroups, update);
    record(
        "upserts of MAPPING_RULE",
        update.getMappingRules(),
        PolicyUpdateRecord::setMappingRules,
        update);
    record("memberships", update.getMemberships(), PolicyUpdateRecord::setMemberships, update);
    record(
        "authorizations",
        update.getAuthorizations(),
        PolicyUpdateRecord::setAuthorizations,
        update);
    record(
        "authorizationDeletes",
        update.getAuthorizationDeletes(),
        PolicyUpdateRecord::setAuthorizationDeletes,
        update);
    record(
        "membershipDeletes",
        update.getMembershipDeletes(),
        PolicyUpdateRecord::setMembershipDeletes,
        update);
    record("deletes", update.getDeletes(), PolicyUpdateRecord::setDeletes, update);
  }

  /** Records the smallest size an entry of the section adds to a chunk, as the splitter counts. */
  private <T> void record(
      final String section,
      final List<T> entries,
      final BiConsumer<PolicyUpdateRecord, List<T>> setter,
      final PolicyUpdateRecord update) {
    final int emptySize = emptyChunk(update).getLength();
    for (final T entry : entries) {
      final var chunk = emptyChunk(update);
      setter.accept(chunk, List.of(entry));
      smallestEntrySizes.merge(section, chunk.getLength() - emptySize, Math::min);
    }
  }

  private static PolicyUpdateRecord emptyChunk(final PolicyUpdateRecord update) {
    return new PolicyUpdateRecord()
        .setPolicyId(update.getPolicyId())
        .setPolicyVersion(update.getPolicyVersion())
        .setBasePolicyVersion(update.getBasePolicyVersion())
        .setSeqNo(update.getSeqNo())
        .setUpdateId(update.getUpdateId());
  }

  private static PolicyUpserts upserts() {
    return new PolicyUpserts(
        ids("tenant").stream().map(id -> new PolicyTenant(id, "Tenant", null)).toList(),
        ids("role").stream().map(id -> new PolicyRole(id, "Role", null)).toList(),
        ids("group").stream().map(id -> new PolicyGroup(id, "Group", null)).toList(),
        ids("rule").stream()
            .map(id -> new PolicyMappingRule(id, "Rule", "department", id))
            .toList(),
        memberships(),
        ids("role").stream()
            .map(
                id ->
                    new PolicyAuthorization(
                        authorizationKey(id), List.of("READ_PROCESS_DEFINITION")))
            .toList());
  }

  private static PolicyDeletions deletions() {
    return new PolicyDeletions(
        ids("tenant"),
        ids("role"),
        ids("group"),
        ids("rule"),
        memberships(),
        ids("role").stream().map(PolicyChangeSetCommandOverheadTest::authorizationKey).toList());
  }

  private static List<PolicyMembership> memberships() {
    return ids("group").stream().map(id -> new PolicyMembership("GROUP", id, "USER", "u")).toList();
  }

  private static PolicyAuthorizationKey authorizationKey(final String roleId) {
    return new PolicyAuthorizationKey("ROLE", roleId, "PROCESS_DEFINITION", "ID", "process", null);
  }

  private static PolicyUpserts noUpserts() {
    return PolicyChangeSets.noUpserts();
  }

  private static PolicyDeletions noDeletions() {
    return PolicyChangeSets.noDeletions();
  }

  private static List<String> ids(final String prefix) {
    return IntStream.range(0, ENTRIES_PER_KIND).mapToObj(index -> prefix + "-" + index).toList();
  }
}

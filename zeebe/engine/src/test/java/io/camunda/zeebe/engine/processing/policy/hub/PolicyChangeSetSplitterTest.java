/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy.hub;

import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.changeSet;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.delta;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noDeletions;
import static io.camunda.zeebe.engine.processing.policy.hub.PolicyChangeSets.noUpserts;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.fanout.protocol.model.PolicyChangeSetKind;
import io.camunda.fanout.protocol.model.PolicyDeletions;
import io.camunda.fanout.protocol.model.PolicyGroup;
import io.camunda.fanout.protocol.model.PolicyMembership;
import io.camunda.fanout.protocol.model.PolicyRole;
import io.camunda.fanout.protocol.model.PolicyTenant;
import io.camunda.fanout.protocol.model.PolicyUpserts;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

final class PolicyChangeSetSplitterTest {

  /** Fits the identifiers and a few entries, so a change set of a dozen entries needs chunks. */
  private static final int SMALL_CHUNK_SIZE = 1024;

  private static final String UPDATE_ID = "update-1";

  @Test
  void shouldPutASmallChangeSetIntoOneChunk() {
    // given
    final var changeSet =
        delta(
            tenants(2),
            new PolicyDeletions(
                List.of("old"), List.of(), List.of(), List.of(), List.of(), List.of()));

    // when
    final var chunks =
        new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE)
            .split(changeSet, 9L, "update-9")
            .get();

    // then
    assertThat(chunks).hasSize(1);
    final var chunk = chunks.getFirst();
    assertThat(chunk.getChunkIndex()).isZero();
    assertThat(chunk.getChunkCount()).isOne();
    assertThat(chunk.getSeqNo()).isEqualTo(9L);
    assertThat(chunk.getUpdateId()).isEqualTo("update-9");
    assertThat(chunk.getTenants()).hasSize(2);
    assertThat(chunk.getDeletes()).extracting(PolicyDeleteEntryValue::getId).containsExactly("old");
  }

  @Test
  void shouldGiveAnEmptyChangeSetOneEmptyChunk() {
    // given an empty change set, which still moves the policy to its next version
    final var changeSet = delta(noUpserts(), noDeletions());

    // when
    final var chunks =
        new PolicyChangeSetSplitter(SMALL_CHUNK_SIZE).split(changeSet, 1L, UPDATE_ID).get();

    // then
    assertThat(chunks).hasSize(1);
    assertThat(chunks.getFirst().getChunkCount()).isOne();
    assertThat(chunks.getFirst().getPolicyVersion()).isEqualTo(changeSet.policyVersion());
  }

  @Test
  void shouldSplitALargeChangeSetIntoChunksThatFit() {
    // given
    final var changeSet = delta(tenants(30), noDeletions());

    // when
    final var chunks =
        new PolicyChangeSetSplitter(SMALL_CHUNK_SIZE).split(changeSet, 1L, UPDATE_ID).get();

    // then every chunk fits, carries the update's identifiers, and knows its place
    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(chunks)
        .allSatisfy(chunk -> assertThat(chunk.getLength()).isLessThanOrEqualTo(SMALL_CHUNK_SIZE));
    assertThat(chunks).extracting(PolicyUpdateRecord::getUpdateId).containsOnly(UPDATE_ID);
    assertThat(chunks)
        .extracting(PolicyUpdateRecord::getChunkIndex)
        .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
    assertThat(chunks).extracting(PolicyUpdateRecord::getChunkCount).containsOnly(chunks.size());
    assertThat(chunks.stream().flatMap(chunk -> chunk.getTenants().stream()))
        .extracting(TenantRecordValue::getTenantId)
        .containsExactlyElementsOf(
            IntStream.range(0, 30).mapToObj(PolicyChangeSetSplitterTest::tenantId).toList());
  }

  @Test
  void shouldNeverExceedTheMaximumChunkSize() {
    // given many small entries in two sections, so that lists grow past 15 entries and the chunk
    // count past 127, whose headers and numbers take more bytes than in an empty chunk
    final var changeSet =
        delta(
            new PolicyUpserts(
                IntStream.range(0, 300)
                    .mapToObj(index -> new PolicyTenant("t" + index, "T", null))
                    .toList(),
                List.of(),
                List.of(),
                List.of(),
                IntStream.range(0, 300)
                    .mapToObj(index -> new PolicyMembership("GROUP", "g", "USER", "u" + index))
                    .toList(),
                List.of()),
            noDeletions());

    // when split with every maximum size in a range
    for (int maxChunkSize = 500; maxChunkSize <= 2500; maxChunkSize++) {
      final int max = maxChunkSize;
      final var chunks = new PolicyChangeSetSplitter(max).split(changeSet, 1L, UPDATE_ID).get();

      // then
      assertThat(chunks)
          .describedAs("chunks of at most %d bytes", max)
          .allSatisfy(chunk -> assertThat(chunk.getLength()).isLessThanOrEqualTo(max));
    }
  }

  @Test
  void shouldRejectAMaximumChunkSizeTooSmallForTheIdentifiers() {
    // given
    final var changeSet = delta(noUpserts(), noDeletions());

    // when
    final var result = new PolicyChangeSetSplitter(10).split(changeSet, 1L, UPDATE_ID);

    // then
    assertThat(result.getLeft()).contains(UPDATE_ID).contains("10 bytes");
  }

  @Test
  void shouldStartANewChunkOnceItHoldsTheMaximumNumberOfEntries() {
    // given two tenants and two roles, and room for three entries per chunk
    final var changeSet =
        delta(
            new PolicyUpserts(
                List.of(new PolicyTenant("t1", "T", null), new PolicyTenant("t2", "T", null)),
                List.of(new PolicyRole("r1", "R", null), new PolicyRole("r2", "R", null)),
                List.of(),
                List.of(),
                List.of(),
                List.of()),
            noDeletions());

    // when
    final var chunks =
        new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE, 3)
            .split(changeSet, 1L, UPDATE_ID)
            .get();

    // then the limit counts the entries of all sections of a chunk
    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).getTenants())
        .extracting(TenantRecordValue::getTenantId)
        .containsExactly("t1", "t2");
    assertThat(chunks.get(0).getRoles())
        .extracting(RoleRecordValue::getRoleId)
        .containsExactly("r1");
    assertThat(chunks.get(1).getTenants()).isEmpty();
    assertThat(chunks.get(1).getRoles())
        .extracting(RoleRecordValue::getRoleId)
        .containsExactly("r2");
  }

  @Test
  void shouldSplitManySmallDeletesByTheirNumber() {
    // given 35,000 tenant deletes, which fit into one chunk of the default size, but whose delete
    // commands would not fit into the batch that applies it
    final var tenantIds = IntStream.range(0, 35_000).mapToObj(index -> "t" + index).toList();
    final var changeSet =
        delta(
            noUpserts(),
            new PolicyDeletions(tenantIds, List.of(), List.of(), List.of(), List.of(), List.of()));

    // when
    final var chunks =
        new PolicyChangeSetSplitter(PolicyChangeSetSplitter.DEFAULT_MAX_CHUNK_SIZE)
            .split(changeSet, 1L, UPDATE_ID)
            .get();

    // then
    assertThat(chunks).hasSize(9);
    assertThat(chunks)
        .allSatisfy(
            chunk ->
                assertThat(chunk.getDeletes())
                    .hasSizeLessThanOrEqualTo(
                        PolicyChangeSetSplitter.DEFAULT_MAX_ENTRIES_PER_CHUNK));
    assertThat(chunks.stream().flatMap(chunk -> chunk.getDeletes().stream()))
        .extracting(PolicyDeleteEntryValue::getId)
        .containsExactlyElementsOf(tenantIds);
  }

  @Test
  void shouldKeepTheApplyOrderAcrossChunks() {
    // given groups, memberships to them, and deletes, which the engine applies in this order
    final var upserts =
        new PolicyUpserts(
            List.of(),
            List.of(),
            IntStream.range(0, 6)
                .mapToObj(index -> new PolicyGroup("group-" + index, "Group " + index, null))
                .toList(),
            List.of(),
            IntStream.range(0, 6)
                .mapToObj(index -> new PolicyMembership("GROUP", "group-" + index, "USER", "u"))
                .toList(),
            List.of());
    final var deletions =
        new PolicyDeletions(
            List.of("tenant-x"), List.of(), List.of("group-x"), List.of(), List.of(), List.of());

    // when
    final var chunks =
        new PolicyChangeSetSplitter(SMALL_CHUNK_SIZE)
            .split(delta(upserts, deletions), 1L, UPDATE_ID)
            .get();

    // then reading the chunks in index order, section by section, gives the change set's order
    final var applied =
        chunks.stream()
            .flatMap(
                chunk ->
                    java.util.stream.Stream.of(
                            chunk.getGroups().stream()
                                .map(GroupRecordValue::getGroupId)
                                .map(id -> "group " + id),
                            chunk.getMemberships().stream()
                                .map(PolicyMembershipEntryValue::getTargetId)
                                .map(id -> "membership " + id),
                            chunk.getDeletes().stream()
                                .map(PolicyDeleteEntryValue::getId)
                                .map(id -> "delete " + id))
                        .flatMap(entries -> entries))
            .toList();
    assertThat(chunks).hasSizeGreaterThan(1);
    assertThat(applied)
        .containsExactly(
            "group group-0",
            "group group-1",
            "group group-2",
            "group group-3",
            "group group-4",
            "group group-5",
            "membership group-0",
            "membership group-1",
            "membership group-2",
            "membership group-3",
            "membership group-4",
            "membership group-5",
            "delete group-x",
            "delete tenant-x");
  }

  @Test
  void shouldSplitTheSameChangeSetIntoTheSameChunks() {
    // given
    final var changeSet = delta(tenants(30), noDeletions());
    final var splitter = new PolicyChangeSetSplitter(SMALL_CHUNK_SIZE);

    // when
    final var first = splitter.split(changeSet, 5L, UPDATE_ID).get();
    final var second = splitter.split(changeSet, 5L, UPDATE_ID).get();

    // then
    assertThat(second).isEqualTo(first);
  }

  @Test
  void shouldRejectAnEntryThatFitsIntoNoChunk() {
    // given
    final var changeSet =
        delta(
            new PolicyUpserts(
                List.of(new PolicyTenant("tenant", "x".repeat(SMALL_CHUNK_SIZE), null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()),
            noDeletions());

    // when
    final var result =
        new PolicyChangeSetSplitter(SMALL_CHUNK_SIZE).split(changeSet, 1L, UPDATE_ID);

    // then
    assertThat(result.getLeft())
        .contains(changeSet.policyId())
        .contains("tenants")
        .contains(Integer.toString(SMALL_CHUNK_SIZE));
  }

  @Test
  void shouldRejectAChangeSetTheMapperRejects() {
    // given
    final var changeSet = changeSet(PolicyChangeSetKind.SNAPSHOT, noUpserts(), noDeletions());

    // when
    final var result =
        new PolicyChangeSetSplitter(SMALL_CHUNK_SIZE).split(changeSet, 1L, UPDATE_ID);

    // then
    assertThat(result.getLeft()).contains("SNAPSHOT");
  }

  private static PolicyUpserts tenants(final int count) {
    return new PolicyUpserts(
        IntStream.range(0, count)
            .mapToObj(index -> new PolicyTenant(tenantId(index), "Tenant " + index, null))
            .toList(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static String tenantId(final int index) {
    return "tenant-" + index;
  }
}

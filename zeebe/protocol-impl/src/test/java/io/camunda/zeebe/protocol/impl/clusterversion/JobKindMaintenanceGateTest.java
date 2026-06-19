/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pins the hazard documented by {@link Capability#JOB_KIND_MAINTENANCE} (catalog ordinal 17): the
 * new {@code JobKind.MAINTENANCE} value must only be stamped onto records above the gate because a
 * pre-feature follower would crash on the first record it tried to deserialize.
 *
 * <p>The test isn't a behavior-toggle gate test (no production processor stamps {@code MAINTENANCE}
 * yet); it's a regression pin that captures three things at the protocol level:
 *
 * <ol>
 *   <li>The current binary can serialize and deserialize a record carrying {@code MAINTENANCE}
 *       without trouble — proves the wire format accepts the new value.
 *   <li>The serialized form encodes the literal name {@code "MAINTENANCE"} (the MsgPack {@code
 *       EnumValue} representation), so a follower's deserialization will absolutely see that name
 *       and try to resolve it locally.
 *   <li>Resolving an unknown name via {@code Enum.valueOf} throws — modelling the exact code path
 *       that a pre-feature follower would trip on. The fact that {@code Enum.valueOf} throws is the
 *       load-bearing failure mode the gate exists to prevent.
 * </ol>
 *
 * <p>A future producer of {@code JobKind.MAINTENANCE} must guard its write on {@code
 * clusterVersionFeatures.isActive(Capability.JOB_KIND_MAINTENANCE)}, and the next gate-style test
 * landing alongside that producer should verify the gating end-to-end. Until then this suite stands
 * as the contract.
 */
final class JobKindMaintenanceGateTest {

  @Nested
  class WireFormat {

    @Test
    void shouldRoundTripJobKindMaintenanceOnCurrentBinary() {
      // given — a JobRecord carrying the new enum value
      final var original =
          new JobRecord()
              .setType("svc")
              .setRetries(3)
              .setDeadline(1_000L)
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
              .setJobKind(JobKind.MAINTENANCE);

      // when — serialize through MsgPack and deserialize into a fresh instance
      final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
      original.write(buffer, 0);
      final var roundTripped = new JobRecord();
      roundTripped.wrap(new UnsafeBuffer(buffer, 0, original.getLength()));

      // then — the new value comes through unchanged on this binary
      assertThat(roundTripped.getJobKind()).isEqualTo(JobKind.MAINTENANCE);
    }

    @Test
    void shouldEncodeJobKindByItsLiteralEnumName() {
      // given — a JobRecord with the new value
      final var record = new JobRecord().setJobKind(JobKind.MAINTENANCE);
      final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
      record.write(buffer, 0);

      // when — examine the serialized bytes
      final var serialized = BufferUtil.bufferAsString(buffer, 0, record.getLength());

      // then — the wire format contains the literal name, so a follower's EnumValue.read
      // will absolutely call Enum.valueOf(JobKind.class, "MAINTENANCE") on it
      assertThat(serialized)
          .as(
              "EnumValue serializes the constant name; the gate prevents this name reaching old binaries")
          .contains(JobKind.MAINTENANCE.name());
    }
  }

  @Nested
  class HazardOnPreFeatureBinary {

    @Test
    void shouldThrowWhenResolvingNameUnknownToTheLocalEnum() {
      // given — a name that the local enum cannot resolve. Modelling: a pre-feature follower's
      // JobKind class lacks MAINTENANCE; its EnumValue.read receives "MAINTENANCE" from the
      // MsgPack stream and reaches Enum.valueOf with that name.
      final var unknownName = "DEFINITELY_NOT_A_JOBKIND_VALUE";

      // when / then — Enum.valueOf throws on the unknown name; this is the failure mode the
      // gate exists to prevent. A processor in the middle of replay would propagate this
      // exception, the partition would wedge, and the operator would have to roll the binary.
      assertThatThrownBy(() -> Enum.valueOf(JobKind.class, unknownName))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(unknownName);
    }

    @Test
    void shouldDocumentTheCatalogGate() {
      // when — the catalog knows the capability
      final var resolved = ClusterVersionCatalog.resolveByName("JOB_KIND_MAINTENANCE");

      // then — it claims ordinal 17 and lists no appliers or commands (the gate is a
      // write-discipline contract, not a versioned-applier/admission-gated-command artifact)
      assertThat(resolved).isPresent();
      assertThat(resolved.get())
          .satisfies(c -> assertThat(c.at()).isEqualTo(17))
          .satisfies(c -> assertThat(c.appliers()).isEmpty())
          .satisfies(c -> assertThat(c.commands()).isEmpty());
    }
  }
}
